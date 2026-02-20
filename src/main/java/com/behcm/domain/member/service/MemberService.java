package com.behcm.domain.member.service;

import com.behcm.domain.member.dto.MemberProfileResponse;
import com.behcm.domain.member.dto.MemberSettingsResponse;
import com.behcm.domain.member.dto.ProfileImageUploadResponse;
import com.behcm.domain.member.dto.UpdateMemberProfileRequest;
import com.behcm.domain.member.dto.UpdateMemberSettingsRequest;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberSettings;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.dto.WorkoutStatsResponse;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutCommentRepository;
import com.behcm.domain.workout.repository.WorkoutLikeRepository;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.service.WorkoutRoomService;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final WorkoutCommentRepository workoutCommentRepository;
    private final WorkoutRoomService workoutRoomService;
    private final MemberSettingsRepository memberSettingsRepository;
    private final S3Service s3Service;

    public ProfileImageUploadResponse uploadProfileImage(Member member, MultipartFile image) {
        String profileUrl = s3Service.uploadProfileImage(image);
        return ProfileImageUploadResponse.of(profileUrl);
    }

    public MemberProfileResponse getMemberProfile(Member member) {
        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMember(member);

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

        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMember(savedMember);
        int currentStreak = calculateCurrentStreak(workoutRecords);
        int longestStreak = calculateLongestStreak(workoutRecords);

        return MemberProfileResponse.from(savedMember, currentStreak, longestStreak);
    }

    public Page<WorkoutFeedItemResponse> getMemberWorkoutFeed(Member member, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return workoutRecordRepository.findAllByMemberPerWorkoutDate(member, pageable).map(WorkoutFeedItemResponse::from);
    }

    public Page<WorkoutFeedItemResponse> getOtherMemberWorkoutFeed(Member currentMember, Long targetMemberId, int page, int size) {
        // 대상 멤버 조회
        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 같은 방 멤버인지 확인
        if (!workoutRoomService.areMembersInSameRoom(currentMember, targetMember)) {
            throw new CustomException(ErrorCode.NOT_SAME_ROOM_MEMBER);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMemberPerWorkoutDate(targetMember, pageable);

        // 좋아요한 workout record ID 목록 조회 (배치 쿼리로 N+1 방지)
        List<Long> workoutRecordIds = workoutRecords.getContent().stream()
                .map(WorkoutRecord::getId)
                .toList();
        List<Long> likedWorkoutRecordIds = workoutLikeRepository.findLikedWorkoutRecordIdsByMemberIdAndWorkoutRecordIds(
                currentMember.getId(), workoutRecordIds);

        // 각 WorkoutRecord에 대해 좋아요 수, 댓글 수, 좋아요 여부 계산
        return workoutRecords.map(workoutRecord -> {
            Long likes = workoutLikeRepository.countByWorkoutRecordId(workoutRecord.getId());
            Long comments = workoutCommentRepository.countByWorkoutRecordId(workoutRecord.getId());
            Boolean isLiked = likedWorkoutRecordIds.contains(workoutRecord.getId());
            return WorkoutFeedItemResponse.from(workoutRecord, likes, comments, isLiked);
        });
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
