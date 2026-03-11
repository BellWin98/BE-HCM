package com.behcm.domain.member.service;

import com.behcm.domain.member.dto.*;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberSettings;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final MemberSettingsRepository memberSettingsRepository;
    private final S3Service s3Service;

    public ProfileImageUploadResponse uploadProfileImage(Member member, MultipartFile image) {
        String profileUrl = s3Service.uploadProfileImage(image);
        return ProfileImageUploadResponse.of(profileUrl);
    }

    public MemberProfileResponse getMemberProfile(Member member) {
        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMemberPerWorkoutDate(member);

        int currentStreak = calculateCurrentStreak(workoutRecords);
        int longestStreak = calculateLongestStreak(workoutRecords);

        return MemberProfileResponse.from(member, currentStreak, longestStreak);
    }

    @Transactional
    public MemberProfileResponse updateMemberProfile(Member member, UpdateMemberProfileRequest request) {
        if (request.getNickname() != null
                && !request.getNickname().equals(member.getNickname())
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        member.updateProfile(request.getNickname(), request.getBio(), request.getProfileUrl());
        Member savedMember = memberRepository.save(member);

        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMemberPerWorkoutDate(savedMember);
        int currentStreak = calculateCurrentStreak(workoutRecords);
        int longestStreak = calculateLongestStreak(workoutRecords);

        return MemberProfileResponse.from(savedMember, currentStreak, longestStreak);
    }

    public Page<WorkoutFeedItemResponse> getMemberWorkoutFeed(Member member, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return workoutRecordRepository.findAllByMemberPerWorkoutDate(member, pageable).map(WorkoutFeedItemResponse::from);
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
