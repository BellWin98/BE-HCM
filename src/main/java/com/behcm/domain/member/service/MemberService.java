package com.behcm.domain.member.service;

import com.behcm.domain.member.dto.MemberProfileResponse;
import com.behcm.domain.member.dto.MemberSettingsResponse;
import com.behcm.domain.member.dto.UpdateMemberProfileRequest;
import com.behcm.domain.member.dto.UpdateMemberSettingsRequest;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberSettings;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.dto.WorkoutStatsResponse;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutLikeRepository;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutLikeRepository workoutLikeRepository;
    private final MemberSettingsRepository memberSettingsRepository;

    public MemberProfileResponse getMemberProfile(Member member) {
        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMember(member);

        int currentStreak = calculateCurrentStreak(workoutRecords);
        int longestStreak = calculateLongestStreak(workoutRecords);

        return MemberProfileResponse.from(member, currentStreak, longestStreak);
    }

    @Transactional
    public MemberProfileResponse updateMemberProfile(Member member, UpdateMemberProfileRequest request) {
        if (!member.getNickname().equals(request.getNickname()) && memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        member.updateProfile(request.getNickname(), request.getBio(), request.getProfileUrl());
        Member savedMember = memberRepository.save(member);

        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMember(savedMember);
        int currentStreak = calculateCurrentStreak(workoutRecords);
        int longestStreak = calculateLongestStreak(workoutRecords);

        return MemberProfileResponse.from(savedMember, currentStreak, longestStreak);
    }

    public Page<WorkoutFeedItemResponse> getMemberWorkoutFeed(Member member, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "workoutDate"));
        return workoutRecordRepository.findAllByMember(member, pageable).map(WorkoutFeedItemResponse::from);
    }

    public WorkoutStatsResponse getMemberWorkoutStats(Member member) {
        List<WorkoutRecord> allRecords = workoutRecordRepository.findAllByMember(member);

        int currentStreak = calculateCurrentStreak(allRecords);
        int longestStreak = calculateLongestStreak(allRecords);

        // 이번 주 운동 횟수
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        int weeklyProgress = (int) allRecords.stream()
                .filter(record -> !record.getWorkoutDate().isBefore(startOfWeek) &&
                                !record.getWorkoutDate().isAfter(endOfWeek))
                .count();

        // 이번 달 운동 횟수
        YearMonth currentMonth = YearMonth.now();
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();
        int monthlyWorkouts = (int) allRecords.stream()
                .filter(record -> !record.getWorkoutDate().isBefore(startOfMonth) &&
                                !record.getWorkoutDate().isAfter(endOfMonth))
                .count();

        // 가장 많이 한 운동 타입 (여러 운동 종류를 모두 고려)
        String favoriteWorkoutType = allRecords.stream()
                .flatMap(record -> record.getWorkoutTypes().stream())
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");

        // 총 운동 시간
        int totalDuration = allRecords.stream()
                .mapToInt(WorkoutRecord::getDuration)
                .sum();

        return WorkoutStatsResponse.builder()
                .totalWorkouts(allRecords.size())
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .weeklyGoal(3) // 기본 주간 목표
                .weeklyProgress(weeklyProgress)
                .monthlyWorkouts(monthlyWorkouts)
                .favoriteWorkoutType(favoriteWorkoutType)
                .totalDuration(totalDuration)
                .build();
    }

    public MemberSettingsResponse getMemberSettings(Member member) {
        MemberSettings settings = memberSettingsRepository.findByMemberId(member.getId())
                .orElseGet(() -> {
                    MemberSettings newSettings = MemberSettings.builder()
                            .member(member)
                            .build();
                    return memberSettingsRepository.save(newSettings);
                });

        return MemberSettingsResponse.from(settings);
    }

    @Transactional
    public MemberSettingsResponse updateMemberSettings(Member member, UpdateMemberSettingsRequest request) {
        MemberSettings settings = memberSettingsRepository.findByMemberId(member.getId())
                .orElseGet(() -> {
                    MemberSettings newSettings = MemberSettings.builder()
                            .member(member)
                            .build();
                    return memberSettingsRepository.save(newSettings);
                });

        if (request.getNotifications() != null) {
            settings.updateNotificationSettings(
                    request.getNotifications().getWorkoutReminder(),
                    request.getNotifications().getPenaltyAlert(),
                    request.getNotifications().getRoomUpdates(),
                    request.getNotifications().getWeeklyReport()
            );
        }

        if (request.getPrivacy() != null) {
            settings.updatePrivacySettings(
                    request.getPrivacy().getShowProfile(),
                    request.getPrivacy().getShowWorkouts(),
                    request.getPrivacy().getShowStats()
            );
        }

        return MemberSettingsResponse.from(settings);
    }

    private int calculateCurrentStreak(List<WorkoutRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }

        List<LocalDate> workoutDates = records.stream()
                .map(WorkoutRecord::getWorkoutDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        LocalDate today = LocalDate.now();
        LocalDate checkDate = today.minusDays(1);

        int streak = workoutDates.contains(today) ? 1 : 0;
        for (LocalDate workoutDate : workoutDates) {
            if (workoutDate.equals(today)) {
                continue;
            }
            if (workoutDate.equals(checkDate)) {
                streak++;
                checkDate = checkDate.minusDays(1);
            } else {
                break;
            }
        }

        return streak;
    }

    private int calculateLongestStreak(List<WorkoutRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }

        List<LocalDate> workoutDates = records.stream()
                .map(WorkoutRecord::getWorkoutDate)
                .distinct()
                .sorted()
                .toList();

        int maxStreak = 1;
        int currentStreak = 1;

        for (int i = 1; i < workoutDates.size(); i++) {
            LocalDate prevDate = workoutDates.get(i - 1);
            LocalDate currDate = workoutDates.get(i);

            if (currDate.equals(prevDate.plusDays(1))) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 1;
            }
        }

        return maxStreak;
    }
}
