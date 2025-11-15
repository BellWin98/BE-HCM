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
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutService {

    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    public WorkoutResponse authenticateWorkout(Member member, WorkoutRequest request) {
        LocalDate workoutDate = LocalDate.parse(request.getWorkoutDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        // 여러 이미지 S3에 업로드
        List<String> imageUrls = s3Service.uploadImages(request.getImages());
        List<WorkoutRoomMember> wrms = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member);
        if (wrms.isEmpty()) {
            throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND);
        }
        for (WorkoutRoomMember wrm : wrms) {
            WorkoutRoom workoutRoom = wrm.getWorkoutRoom();
            // 중복 운동 인증 체크
            if (workoutRecordRepository.existsByMemberAndWorkoutRoomAndWorkoutDate(member, workoutRoom, workoutDate)) {
                throw new CustomException(ErrorCode.WORKOUT_ALREADY_AUTHENTICATED);
            }
            // 운동 기록 저장
            WorkoutRecord workoutRecord = WorkoutRecord.builder()
                    .member(member)
                    .workoutRoom(workoutRoom)
                    .workoutDate(workoutDate)
                    .workoutTypes(request.getWorkoutTypes())
                    .duration(request.getDuration())
                    .imageUrls(imageUrls)
                    .build();
            WorkoutRecord savedWorkoutRecord = workoutRecordRepository.save(workoutRecord);
            wrm.updateTotalWorkouts(wrm.getTotalWorkouts() + 1);
            // 이번주가 아닌 날짜는 이번주 운동 횟수에 반영 안함
            if (isThisWeek(savedWorkoutRecord.getWorkoutDate())) {
                wrm.updateWeeklyWorkouts(wrm.getWeeklyWorkouts() + 1);
            }
        }
        member.updateTotalWorkoutDays(member.getTotalWorkoutDays() + 1);
        memberRepository.save(member);

        return new WorkoutResponse(workoutDate, request.getWorkoutTypes(), request.getDuration(), imageUrls);
    }

    private boolean isThisWeek(LocalDate targetDate) {
        if (targetDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();

        // 이번주의 시작일 (월요일)
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // 이번주의 마지막일 (일요일)
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // 경계값 포함: startOfWeek <= targetDate <= endOfWeek
        return !targetDate.isBefore(startOfWeek) && !targetDate.isAfter(endOfWeek);
    }
}