package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.dto.ReactionMemberResponse;
import com.behcm.domain.social.dto.ReactionRequest;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.entity.ReactionEmoji;
import com.behcm.domain.social.entity.WorkoutReaction;
import com.behcm.domain.social.repository.WorkoutReactionRepository;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutReactionService {

    private final WorkoutReactionRepository workoutReactionRepository;
    private final WorkoutRecordAccessGuard workoutRecordAccessGuard;
    private final WorkoutSocialQueryService workoutSocialQueryService;

    /**
     * 리액션을 남기거나 이미 남긴 이모지를 바꾼다.
     *
     * 한 회원은 인증 하나에 이모지를 하나만 남길 수 있으므로(uk_workout_reaction_record_member),
     * 다른 이모지를 누르면 행을 추가하지 않고 기존 행을 갱신한다.
     */
    public WorkoutSocialSummary react(Member member, Long recordId, ReactionRequest request) {
        ReactionEmoji emoji = parseEmoji(request.getEmoji());
        WorkoutRecord workoutRecord = workoutRecordAccessGuard.getAccessibleRecord(member, recordId);

        workoutReactionRepository.findByWorkoutRecordAndMember(workoutRecord, member)
                .ifPresentOrElse(
                        reaction -> reaction.changeEmoji(emoji),
                        () -> workoutReactionRepository.save(WorkoutReaction.builder()
                                .workoutRecord(workoutRecord)
                                .member(member)
                                .emoji(emoji)
                                .build())
                );

        return summarize(recordId, member);
    }

    public WorkoutSocialSummary cancelReaction(Member member, Long recordId) {
        WorkoutRecord workoutRecord = workoutRecordAccessGuard.getAccessibleRecord(member, recordId);

        WorkoutReaction reaction = workoutReactionRepository.findByWorkoutRecordAndMember(workoutRecord, member)
                .orElseThrow(() -> new CustomException(ErrorCode.REACTION_NOT_FOUND));
        workoutReactionRepository.delete(reaction);

        return summarize(recordId, member);
    }

    @Transactional(readOnly = true)
    public List<ReactionMemberResponse> getReactionMembers(Member member, Long recordId) {
        WorkoutRecord workoutRecord = workoutRecordAccessGuard.getAccessibleRecord(member, recordId);

        return workoutReactionRepository.findAllByWorkoutRecordFetchMember(workoutRecord).stream()
                .map(ReactionMemberResponse::from)
                .toList();
    }

    private WorkoutSocialSummary summarize(Long recordId, Member member) {
        return workoutSocialQueryService.summarize(List.of(recordId), member)
                .getOrDefault(recordId, WorkoutSocialSummary.empty());
    }

    private ReactionEmoji parseEmoji(String emoji) {
        try {
            return ReactionEmoji.valueOf(emoji.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.UNSUPPORTED_REACTION);
        }
    }
}
