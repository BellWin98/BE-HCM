package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.workout.dto.WorkoutRequest;
import com.behcm.domain.workout.dto.WorkoutResponse;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutService {

    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    public WorkoutResponse authenticateWorkout(Member member, WorkoutRequest request) {
        // 현재 참여중인 운동방 조회
        WorkoutRoomMember workoutRoomMember = workoutRoomMemberRepository.findByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        WorkoutRoom workoutRoom = workoutRoomMember.getWorkoutRoom();
        
        // 운동 날짜 파싱
        LocalDate workoutDate = LocalDate.parse(request.getWorkoutDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        
        // 중복 운동 인증 체크
        if (workoutRecordRepository.existsByMemberAndWorkoutRoomAndWorkoutDate(member, workoutRoom, workoutDate)) {
            throw new CustomException(ErrorCode.WORKOUT_ALREADY_AUTHENTICATED);
        }
        
        // 이미지 S3에 업로드
        String imageUrl = s3Service.uploadWorkoutImage(request.getImage());
        
        // 운동 기록 저장
        WorkoutRecord workoutRecord = WorkoutRecord.builder()
                .member(member)
                .workoutRoom(workoutRoom)
                .workoutDate(workoutDate)
                .workoutType(request.getWorkoutType())
                .duration(request.getDuration())
                .imageUrl(imageUrl)
                .build();
        
        WorkoutRecord savedWorkoutRecord = workoutRecordRepository.save(workoutRecord);
        workoutRoomMember.updateTotalWorkouts(workoutRoomMember.getTotalWorkouts() + 1);
        workoutRoomMember.updateWeeklyWorkouts(workoutRoomMember.getWeeklyWorkouts() + 1);
        member.updateTotalWorkoutDays(member.getTotalWorkoutDays() + 1);
        memberRepository.save(member);
        
        return WorkoutResponse.from(savedWorkoutRecord);
    }
}