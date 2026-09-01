package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.social.dto.GroupedSocialSummary;
import com.behcm.domain.social.dto.ReactionCountResponse;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.entity.ReactionEmoji;
import com.behcm.domain.social.repository.WorkoutCommentRepository;
import com.behcm.domain.social.repository.WorkoutReactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WorkoutSocialQueryServiceTest {

    @Mock
    private WorkoutReactionRepository workoutReactionRepository;

    @Mock
    private WorkoutCommentRepository workoutCommentRepository;

    @InjectMocks
    private WorkoutSocialQueryService workoutSocialQueryService;

    private Member viewer() {
        Member member = Member.builder()
                .email("viewer@test.com")
                .nickname("viewer")
                .role(MemberRole.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Test
    @DisplayName("summarize는 이모지별 리액션 수를 많은 순으로 정렬하고 내가 누른 이모지를 표시한다")
    void summarize_aggregatesReactionsAndMarksMine() {
        Member viewer = viewer();
        given(workoutReactionRepository.countByRecordIdsGroupByEmoji(anyCollection())).willReturn(List.<Object[]>of(
                new Object[]{10L, ReactionEmoji.CLAP, 1L},
                new Object[]{10L, ReactionEmoji.MUSCLE, 3L},
                new Object[]{10L, ReactionEmoji.FIRE, 2L}
        ));
        given(workoutReactionRepository.findEmojiByRecordIdsAndMember(anyCollection(), any(Member.class)))
                .willReturn(List.<Object[]>of(new Object[]{10L, ReactionEmoji.FIRE}));
        given(workoutCommentRepository.countByRecordIds(anyCollection()))
                .willReturn(List.<Object[]>of(new Object[]{10L, 4L}));

        Map<Long, WorkoutSocialSummary> result = workoutSocialQueryService.summarize(List.of(10L), viewer);

        WorkoutSocialSummary summary = result.get(10L);
        assertThat(summary.getCommentCount()).isEqualTo(4L);
        assertThat(summary.getReactions())
                .extracting(ReactionCountResponse::getEmoji, ReactionCountResponse::getCount, ReactionCountResponse::isReactedByMe)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MUSCLE", 3L, false),
                        org.assertj.core.groups.Tuple.tuple("FIRE", 2L, true),
                        org.assertj.core.groups.Tuple.tuple("CLAP", 1L, false)
                );
        assertThat(summary.getReactions().getFirst().getSymbol()).isEqualTo(ReactionEmoji.MUSCLE.getSymbol());
    }

    @Test
    @DisplayName("summarize는 리액션과 댓글이 없는 운동 기록에도 빈 요약을 채워 넣는다")
    void summarize_recordWithoutSocialData_returnsEmptySummary() {
        given(workoutReactionRepository.countByRecordIdsGroupByEmoji(anyCollection())).willReturn(List.of());
        given(workoutReactionRepository.findEmojiByRecordIdsAndMember(anyCollection(), any(Member.class))).willReturn(List.of());
        given(workoutCommentRepository.countByRecordIds(anyCollection())).willReturn(List.of());

        Map<Long, WorkoutSocialSummary> result = workoutSocialQueryService.summarize(List.of(7L), viewer());

        assertThat(result).containsOnlyKeys(7L);
        assertThat(result.get(7L).getReactions()).isEmpty();
        assertThat(result.get(7L).getCommentCount()).isZero();
    }

    @Test
    @DisplayName("summarize는 대상이 비어 있으면 쿼리를 실행하지 않는다")
    void summarize_emptyIds_skipsQueries() {
        Map<Long, WorkoutSocialSummary> result = workoutSocialQueryService.summarize(List.of(), viewer());

        assertThat(result).isEmpty();
        verifyNoInteractions(workoutReactionRepository, workoutCommentRepository);
    }

    @Test
    @DisplayName("summarize는 조회 주체가 없으면 내 리액션 조회를 생략한다")
    void summarize_nullViewer_skipsMyReactionQuery() {
        given(workoutReactionRepository.countByRecordIdsGroupByEmoji(anyCollection())).willReturn(List.of());
        given(workoutCommentRepository.countByRecordIds(anyCollection())).willReturn(List.of());

        workoutSocialQueryService.summarize(List.of(7L), null);

        verify(workoutReactionRepository, never()).findEmojiByRecordIdsAndMember(anyCollection(), any());
    }

    @Test
    @DisplayName("summarizeGrouped는 같은 날 여러 방에 저장된 인증의 리액션/댓글을 그룹 합계로 집계한다")
    void summarizeGrouped_aggregatesSiblingRecords() {
        Member viewer = viewer();
        // 대표 10L, 형제 11L. 2번 회원은 두 방 모두에서 💪를 눌렀다.
        given(workoutReactionRepository.findMemberEmojiByRecordIds(anyCollection())).willReturn(List.<Object[]>of(
                new Object[]{10L, 2L, ReactionEmoji.MUSCLE},
                new Object[]{11L, 2L, ReactionEmoji.MUSCLE},
                new Object[]{11L, 3L, ReactionEmoji.MUSCLE},
                new Object[]{11L, 1L, ReactionEmoji.FIRE}
        ));
        given(workoutCommentRepository.countByRecordIds(anyCollection())).willReturn(List.<Object[]>of(
                new Object[]{10L, 2L},
                new Object[]{11L, 3L}
        ));

        Map<Long, Collection<Long>> groups = Map.of(10L, List.of(10L, 11L));
        Map<Long, GroupedSocialSummary> result = workoutSocialQueryService.summarizeGrouped(groups, viewer);

        GroupedSocialSummary grouped = result.get(10L);
        // 💪는 2번 회원이 두 번 눌렀어도 1명으로 센다 → 2명(2번, 3번)
        assertThat(grouped.getTotal().getReactions())
                .extracting(ReactionCountResponse::getEmoji, ReactionCountResponse::getCount, ReactionCountResponse::isReactedByMe)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MUSCLE", 2L, false),
                        org.assertj.core.groups.Tuple.tuple("FIRE", 1L, true)
                );
        assertThat(grouped.getTotal().getCommentCount()).isEqualTo(5L);

        // 방별 내역은 인증 단위 그대로 유지된다
        assertThat(grouped.getByRecordId().get(10L).getCommentCount()).isEqualTo(2L);
        assertThat(grouped.getByRecordId().get(10L).getReactions())
                .extracting(ReactionCountResponse::getEmoji, ReactionCountResponse::getCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MUSCLE", 1L));
        assertThat(grouped.getByRecordId().get(11L).getReactions())
                .extracting(ReactionCountResponse::getEmoji, ReactionCountResponse::getCount, ReactionCountResponse::isReactedByMe)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MUSCLE", 2L, false),
                        org.assertj.core.groups.Tuple.tuple("FIRE", 1L, true)
                );
    }

    @Test
    @DisplayName("summarizeGrouped는 반응이 하나도 없는 그룹에도 빈 요약을 채워 넣는다")
    void summarizeGrouped_withoutSocialData_returnsEmptySummary() {
        given(workoutReactionRepository.findMemberEmojiByRecordIds(anyCollection())).willReturn(List.of());
        given(workoutCommentRepository.countByRecordIds(anyCollection())).willReturn(List.of());

        Map<Long, GroupedSocialSummary> result =
                workoutSocialQueryService.summarizeGrouped(Map.of(7L, List.of(7L, 8L)), viewer());

        GroupedSocialSummary grouped = result.get(7L);
        assertThat(grouped.getTotal().getReactions()).isEmpty();
        assertThat(grouped.getTotal().getCommentCount()).isZero();
        assertThat(grouped.getByRecordId()).containsOnlyKeys(7L, 8L);
    }

    @Test
    @DisplayName("summarizeGrouped는 대상이 비어 있으면 쿼리를 실행하지 않는다")
    void summarizeGrouped_emptyGroups_skipsQueries() {
        assertThat(workoutSocialQueryService.summarizeGrouped(Map.of(), viewer())).isEmpty();

        verifyNoInteractions(workoutReactionRepository, workoutCommentRepository);
    }
}
