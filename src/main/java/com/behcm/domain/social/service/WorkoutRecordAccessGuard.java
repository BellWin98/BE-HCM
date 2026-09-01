package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리액션/댓글의 공통 접근 제어.
 *
 * 운동 인증은 그 인증이 올라간 운동방의 멤버만 볼 수 있으므로, 리액션과 댓글도 같은 기준을 따른다.
 */
@Component
@RequiredArgsConstructor
public class WorkoutRecordAccessGuard {

    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Transactional(readOnly = true)
    public WorkoutRecord getAccessibleRecord(Member member, Long recordId) {
        WorkoutRecord workoutRecord = workoutRecordRepository.findById(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_RECORD_NOT_FOUND));

        if (!workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(member, workoutRecord.getWorkoutRoom())) {
            throw new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER);
        }

        return workoutRecord;
    }
}
