package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutLike;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutLikeRepository;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutLikeService {

    private final WorkoutLikeRepository workoutLikeRepository;
    private final WorkoutRecordRepository workoutRecordRepository;

    @Transactional
    public void likeWorkout(Member member, Long workoutId) {
        WorkoutRecord workoutRecord = workoutRecordRepository.findById(workoutId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_RECORD_NOT_FOUND));

        // 이미 좋아요를 눌렀는지 확인
        if (workoutLikeRepository.existsByMemberIdAndWorkoutRecordId(member.getId(), workoutId)) {
            throw new CustomException(ErrorCode.ALREADY_LIKED);
        }

        WorkoutLike workoutLike = WorkoutLike.builder()
                .member(member)
                .workoutRecord(workoutRecord)
                .build();

        workoutLikeRepository.save(workoutLike);
    }

    @Transactional
    public void unlikeWorkout(Member member, Long workoutId) {
        WorkoutRecord workoutRecord = workoutRecordRepository.findById(workoutId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_RECORD_NOT_FOUND));

        // 좋아요를 누른 적이 있는지 확인
        if (!workoutLikeRepository.existsByMemberIdAndWorkoutRecordId(member.getId(), workoutId)) {
            throw new CustomException(ErrorCode.LIKE_NOT_FOUND);
        }

        workoutLikeRepository.deleteByMemberIdAndWorkoutRecordId(member.getId(), workoutId);
    }
}
