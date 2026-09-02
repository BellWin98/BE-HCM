package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.dto.GroupedSocialSummary;
import com.behcm.domain.social.dto.ReactionCountResponse;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.entity.ReactionEmoji;
import com.behcm.domain.social.repository.WorkoutCommentRepository;
import com.behcm.domain.social.repository.WorkoutReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 인증에 달린 리액션/댓글 집계 조회.
 *
 * 피드와 운동방 상세는 화면 하나에 인증을 수십 건 그리므로, 항상 인증 id 목록을 한 번에 받아
 * 쿼리 3개(리액션 집계 / 내 리액션 / 댓글 수)로 끝낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutSocialQueryService {

    private final WorkoutReactionRepository workoutReactionRepository;
    private final WorkoutCommentRepository workoutCommentRepository;

    /**
     * @param viewer 조회 주체. null 이면 "내가 누른 리액션" 표시를 생략한다.
     * @return 요청한 모든 인증 id 가 키로 존재하는 맵(리액션/댓글이 없으면 빈 요약).
     */
    public Map<Long, WorkoutSocialSummary> summarize(Collection<Long> recordIds, Member viewer) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new LinkedHashSet<>(recordIds);

        Map<Long, Map<ReactionEmoji, Long>> countsByRecordId = new HashMap<>();
        for (Object[] row : workoutReactionRepository.countByRecordIdsGroupByEmoji(ids)) {
            Long recordId = (Long) row[0];
            ReactionEmoji emoji = (ReactionEmoji) row[1];
            Long count = (Long) row[2];
            countsByRecordId
                    .computeIfAbsent(recordId, key -> new EnumMap<>(ReactionEmoji.class))
                    .put(emoji, count);
        }

        Map<Long, ReactionEmoji> myEmojiByRecordId = new HashMap<>();
        if (viewer != null) {
            for (Object[] row : workoutReactionRepository.findEmojiByRecordIdsAndMember(ids, viewer)) {
                myEmojiByRecordId.put((Long) row[0], (ReactionEmoji) row[1]);
            }
        }

        Map<Long, Long> commentCountByRecordId = new HashMap<>();
        for (Object[] row : workoutCommentRepository.countByRecordIds(ids)) {
            commentCountByRecordId.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, WorkoutSocialSummary> summaries = new HashMap<>();
        for (Long recordId : ids) {
            summaries.put(recordId, WorkoutSocialSummary.builder()
                    .reactions(toReactionCounts(
                            countsByRecordId.getOrDefault(recordId, Map.of()),
                            myEmojiByRecordId.get(recordId)
                    ))
                    .commentCount(commentCountByRecordId.getOrDefault(recordId, 0L))
                    .build());
        }
        return summaries;
    }

    /**
     * 같은 운동이 방마다 한 건씩 저장되는 구조를 감안해, 그룹 단위로 집계한다.
     *
     * 마이페이지 피드는 하루 한 장만 그리려고 대표 인증 한 건만 뽑는데, 리액션/댓글은 각 방의 인증에
     * 따로 달리므로 대표 한 건만 세면 다른 방의 반응이 통째로 누락된다. 그래서 그날의 형제 인증을 모두
     * 받아 합계를 내고, 방별 내역도 함께 돌려준다.
     *
     * @param groups key = 대표 인증 id, value = 같은 그룹(같은 날 같은 회원)의 인증 id 전체
     * @param viewer 조회 주체. null 이면 "내가 누른 리액션" 표시를 생략한다.
     * @return 요청한 모든 그룹 key 가 키로 존재하는 맵
     */
    public Map<Long, GroupedSocialSummary> summarizeGrouped(Map<Long, ? extends Collection<Long>> groups, Member viewer) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = groups.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }

        // 합계에서 회원 기준 중복을 제거해야 하므로 집계된 수가 아니라 행 그대로 받는다.
        // (recordId, memberId, emoji)
        Map<Long, Map<ReactionEmoji, Set<Long>>> membersByRecordId = new HashMap<>();
        for (Object[] row : workoutReactionRepository.findMemberEmojiByRecordIds(ids)) {
            Long recordId = (Long) row[0];
            Long memberId = (Long) row[1];
            ReactionEmoji emoji = (ReactionEmoji) row[2];
            membersByRecordId
                    .computeIfAbsent(recordId, key -> new EnumMap<>(ReactionEmoji.class))
                    .computeIfAbsent(emoji, key -> new LinkedHashSet<>())
                    .add(memberId);
        }

        Map<Long, Long> commentCountByRecordId = new HashMap<>();
        for (Object[] row : workoutCommentRepository.countByRecordIds(ids)) {
            commentCountByRecordId.put((Long) row[0], (Long) row[1]);
        }

        Long viewerId = viewer != null ? viewer.getId() : null;
        Map<Long, GroupedSocialSummary> result = new HashMap<>();
        groups.forEach((groupKey, recordIds) -> {
            Map<ReactionEmoji, Set<Long>> groupMembers = new EnumMap<>(ReactionEmoji.class);
            Map<Long, WorkoutSocialSummary> byRecordId = new HashMap<>();
            long totalCommentCount = 0L;

            for (Long recordId : recordIds) {
                Map<ReactionEmoji, Set<Long>> perRecord =
                        membersByRecordId.getOrDefault(recordId, Map.of());
                perRecord.forEach((emoji, memberIds) ->
                        groupMembers.computeIfAbsent(emoji, key -> new LinkedHashSet<>()).addAll(memberIds));

                long commentCount = commentCountByRecordId.getOrDefault(recordId, 0L);
                totalCommentCount += commentCount;
                byRecordId.put(recordId, WorkoutSocialSummary.builder()
                        .reactions(toReactionCounts(countBy(perRecord), myEmojis(perRecord, viewerId)))
                        .commentCount(commentCount)
                        .build());
            }

            result.put(groupKey, GroupedSocialSummary.builder()
                    .total(WorkoutSocialSummary.builder()
                            .reactions(toReactionCounts(countBy(groupMembers), myEmojis(groupMembers, viewerId)))
                            .commentCount(totalCommentCount)
                            .build())
                    .byRecordId(byRecordId)
                    .build());
        });
        return result;
    }

    private Map<ReactionEmoji, Long> countBy(Map<ReactionEmoji, Set<Long>> membersByEmoji) {
        Map<ReactionEmoji, Long> counts = new EnumMap<>(ReactionEmoji.class);
        membersByEmoji.forEach((emoji, memberIds) -> counts.put(emoji, (long) memberIds.size()));
        return counts;
    }

    // 방마다 다른 이모지를 눌렀을 수 있으므로 하나가 아니라 집합으로 본다.
    private Set<ReactionEmoji> myEmojis(Map<ReactionEmoji, Set<Long>> membersByEmoji, Long viewerId) {
        if (viewerId == null) {
            return Set.of();
        }
        Set<ReactionEmoji> mine = EnumSet.noneOf(ReactionEmoji.class);
        membersByEmoji.forEach((emoji, memberIds) -> {
            if (memberIds.contains(viewerId)) {
                mine.add(emoji);
            }
        });
        return mine;
    }

    public WorkoutSocialSummary summarize(Long recordId, Member viewer) {
        return summarize(List.of(recordId), viewer)
                .getOrDefault(recordId, WorkoutSocialSummary.empty());
    }

    private List<ReactionCountResponse> toReactionCounts(Map<ReactionEmoji, Long> counts, ReactionEmoji myEmoji) {
        return toReactionCounts(counts, myEmoji == null ? Set.of() : Set.of(myEmoji));
    }

    // 많이 눌린 이모지가 앞에 오게 하고, 동수면 enum 선언 순서로 고정해 응답이 흔들리지 않게 한다.
    private List<ReactionCountResponse> toReactionCounts(Map<ReactionEmoji, Long> counts, Set<ReactionEmoji> myEmojis) {
        return counts.entrySet().stream()
                .sorted(Comparator
                        .comparingLong((Map.Entry<ReactionEmoji, Long> entry) -> entry.getValue()).reversed()
                        .thenComparing(entry -> entry.getKey().ordinal()))
                .map(entry -> ReactionCountResponse.of(entry.getKey(), entry.getValue(), myEmojis.contains(entry.getKey())))
                .toList();
    }
}
