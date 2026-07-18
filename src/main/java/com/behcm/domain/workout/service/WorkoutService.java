package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.notification.service.NotificationFacade;
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
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
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

    private static final String WEEKLY_GOAL_ACHIEVED_TYPE = "WEEKLY_GOAL_ACHIEVED";

    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;
    private final CacheManager cacheManager;
    private final NotificationFacade notificationFacade;

    @CacheEvict(value = "workoutRoomDetail", allEntries = true)
    public WorkoutResponse authenticateWorkout(Member member, WorkoutRequest request) {
        LocalDate workoutDate = LocalDate.parse(request.getWorkoutDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        List<WorkoutRoomMember> wrms = workoutRoomMemberRepository.findByMember(member);
        if (wrms.isEmpty()) {
            throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND);
        }

        // 멤버가 참여 중인 모든 방에 대해, 해당 날짜에 이미 운동 인증이 존재하는지 한 번의 쿼리로 검사
        List<WorkoutRoom> workoutRooms = wrms.stream()
                .map(WorkoutRoomMember::getWorkoutRoom)
                .toList();
        List<WorkoutRecord> existingRecords = workoutRecordRepository.findByMemberAndWorkoutDateAndWorkoutRoomIn(
                member,
                workoutDate,
                workoutRooms
        );
        if (!existingRecords.isEmpty()) {
            throw new CustomException(ErrorCode.WORKOUT_ALREADY_AUTHENTICATED);
        }

        // 여러 이미지 S3에 업로드
        List<String> imageUrls = s3Service.uploadWorkoutImages(request.getImages());
        for (WorkoutRoomMember wrm : wrms) {
            WorkoutRoom workoutRoom = wrm.getWorkoutRoom();
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
                int weeklyWorkoutsBeforeUpdate = wrm.getWeeklyWorkouts();
                wrm.updateWeeklyWorkouts(weeklyWorkoutsBeforeUpdate + 1);
                if (hasJustReachedWeeklyGoal(weeklyWorkoutsBeforeUpdate, wrm.getWeeklyWorkouts(), workoutRoom.getMinWeeklyWorkouts())) {
                    notifyWeeklyGoalAchieved(member, workoutRoom);
                }
            }
        }
        member.updateTotalWorkoutDays(member.getTotalWorkoutDays() + 1);
        Member savedMember = memberRepository.save(member);

        return new WorkoutResponse(workoutDate, request.getWorkoutTypes(), request.getDuration(), imageUrls, savedMember.getTotalWorkoutDays());
    }

    // 이번 운동 인증으로 주간 목표 횟수를 처음 채웠는지 여부 (목표 달성 이후 추가 인증 시 재알림 방지)
    private boolean hasJustReachedWeeklyGoal(int weeklyWorkoutsBeforeUpdate, int weeklyWorkoutsAfterUpdate, int minWeeklyWorkouts) {
        return weeklyWorkoutsBeforeUpdate < minWeeklyWorkouts && weeklyWorkoutsAfterUpdate >= minWeeklyWorkouts;
    }

    private void notifyWeeklyGoalAchieved(Member member, WorkoutRoom workoutRoom) {
        String title = "🎉 주간 운동 목표 달성!";
        String body = String.format("%s님이 이번 주 운동 목표(%d회)를 달성했어요!",
                member.getNickname(), workoutRoom.getMinWeeklyWorkouts());
        notificationFacade.notifyRoomMembers(workoutRoom.getId(), member, title, body, WEEKLY_GOAL_ACHIEVED_TYPE, "");
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