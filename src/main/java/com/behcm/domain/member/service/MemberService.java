package com.behcm.domain.member.service;

import com.behcm.domain.member.dto.*;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberSettings;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.social.dto.GroupedSocialSummary;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.service.WorkoutSocialQueryService;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.dto.WorkoutFeedRoomResponse;
import com.behcm.domain.workout.enums.PeriodType;
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
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.Collection;
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
    private final MemberSettingsRepository memberSettingsRepository;
    private final WorkoutSocialQueryService workoutSocialQueryService;
    private final S3Service s3Service;

    public ProfileImageUploadResponse uploadProfileImage(Member member, MultipartFile image) {
        String profileUrl = s3Service.uploadProfileImage(image);
        return ProfileImageUploadResponse.of(profileUrl);
    }

//    @Cacheable(value = "memberProfile", key = "#member.id")
    public MemberProfileResponse getMemberProfile(Member member) {
        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByMemberPerWorkoutDate(member);

        int currentStreak = calculateCurrentStreak(workoutRecords);
        int longestStreak = calculateLongestStreak(workoutRecords);

        return MemberProfileResponse.from(member, currentStreak, longestStreak);
    }

    @Transactional
//    @CacheEvict(value = "memberProfile", key = "#member.id")
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

    public Page<WorkoutFeedItemResponse> getMemberWorkoutFeed(Member member, int page, int size, PeriodType periodType) {
        Pageable pageable = PageRequest.of(page, size);

        PeriodType effectivePeriodType = periodType == null ? PeriodType.ALL : periodType;
        LocalDate today = LocalDate.now();

        LocalDate startDate;
        LocalDate endDate;

        if (effectivePeriodType == PeriodType.WEEK) {
            LocalDate weekStart = today.with(DayOfWeek.MONDAY);
            if (today.getDayOfWeek().getValue() < DayOfWeek.MONDAY.getValue()) {
                weekStart = today.minusDays(today.getDayOfWeek().getValue() + (7 - DayOfWeek.MONDAY.getValue()));
            }
            startDate = weekStart;
            endDate = weekStart.plusDays(6);
        } else {
            YearMonth currentMonth = YearMonth.from(today);
            startDate = currentMonth.atDay(1);
            endDate = currentMonth.atEndOfMonth();
        }

        if (effectivePeriodType == PeriodType.ALL) {
            return toFeed(workoutRecordRepository.findAllByMemberPerWorkoutDate(member, pageable), member);
        }

        return toFeed(
                workoutRecordRepository.findAllByMemberPerWorkoutDateAndWorkoutDateBetween(member, startDate, endDate, pageable),
                member
        );
    }

    /**
     * 대표 인증 목록에 리액션/댓글을 채워 피드로 만든다.
     *
     * 운동 인증 한 번은 참여 중인 방 수만큼 레코드로 저장되고 피드 쿼리는 그중 대표 한 건만 뽑는데,
     * 리액션/댓글은 각 방의 레코드에 따로 달린다. 그래서 대표 한 건이 아니라 그날의 형제 레코드 전체를
     * 집계 대상으로 넘긴다. 집계 대상은 페이지 전체를 한 번에 모아 조회한다 — 건별로 조회하면 그대로
     * N+1 이 된다.
     */
    private Page<WorkoutFeedItemResponse> toFeed(Page<WorkoutRecord> workoutRecords, Member viewer) {
        List<LocalDate> workoutDates = workoutRecords.getContent().stream()
                .map(WorkoutRecord::getWorkoutDate)
                .distinct()
                .toList();
        if (workoutDates.isEmpty()) {
            return workoutRecords.map(workoutRecord ->
                    WorkoutFeedItemResponse.of(workoutRecord, WorkoutSocialSummary.empty(), List.of()));
        }

        Map<LocalDate, List<RoomRecordRef>> refsByDate = workoutRecordRepository
                .findRoomBreakdownByMemberAndWorkoutDateIn(viewer, workoutDates).stream()
                .map(row -> new RoomRecordRef((LocalDate) row[0], (Long) row[1], (Long) row[2], (String) row[3]))
                .collect(Collectors.groupingBy(RoomRecordRef::workoutDate));

        Map<Long, Collection<Long>> groups = workoutRecords.getContent().stream()
                .collect(Collectors.toMap(
                        WorkoutRecord::getId,
                        workoutRecord -> refsByDate.getOrDefault(workoutRecord.getWorkoutDate(), List.of()).stream()
                                .map(RoomRecordRef::recordId)
                                .collect(Collectors.<Long>toList())
                ));
        Map<Long, GroupedSocialSummary> socialByRepresentativeId =
                workoutSocialQueryService.summarizeGrouped(groups, viewer);

        return workoutRecords.map(workoutRecord -> {
            GroupedSocialSummary grouped = socialByRepresentativeId
                    .getOrDefault(workoutRecord.getId(), GroupedSocialSummary.empty());
            List<WorkoutFeedRoomResponse> rooms = refsByDate
                    .getOrDefault(workoutRecord.getWorkoutDate(), List.of()).stream()
                    .map(ref -> WorkoutFeedRoomResponse.of(
                            ref.roomId(), ref.roomName(), ref.recordId(), grouped.getByRecordId().get(ref.recordId())))
                    .toList();
            return WorkoutFeedItemResponse.of(workoutRecord, grouped.getTotal(), rooms);
        });
    }

    /** findRoomBreakdownByMemberAndWorkoutDateIn 의 (workoutDate, recordId, roomId, roomName) 행. */
    private record RoomRecordRef(LocalDate workoutDate, Long recordId, Long roomId, String roomName) {
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
