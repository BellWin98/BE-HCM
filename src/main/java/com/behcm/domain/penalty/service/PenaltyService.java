package com.behcm.domain.penalty.service;

import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.penalty.dto.*;
import com.behcm.domain.penalty.entity.PenaltyAccount;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyAccountRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PenaltyService {

    private static final String PENALTY_ASSIGNED_TYPE = "PENALTY_ASSIGNED";

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;
    private final PenaltyAccountRepository penaltyAccountRepository;
    private final PenaltyRepository penaltyRepository;
    private final NotificationFacade notificationFacade;

    @Transactional
    public void calculateAndAssignPenalties() {
        LocalDate today = LocalDate.now();
        LocalDate lastWeekStart = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

        log.info("Weekly penalty calculation started (week={}~{})", lastWeekStart, lastWeekEnd);

        List<WorkoutRoom> activeWorkoutRooms = workoutRoomRepository.findByIsActiveTrueAndPenaltyEnabledTrueFetchMembers();

        int assigned = 0;
        long totalAmount = 0L;
        for (WorkoutRoom workoutRoom : activeWorkoutRooms) {
            List<Penalty> penalties = processWorkoutRoomPenalties(workoutRoom, lastWeekStart, lastWeekEnd);
            assigned += penalties.size();
            totalAmount += penalties.stream().mapToLong(Penalty::getPenaltyAmount).sum();
        }

        // 다음 주 월요일에 "벌금이 안 나갔다"는 문의가 오면 이 한 줄이 출발점이다.
        log.info("Weekly penalty calculation completed (rooms={}, penalties={}, totalAmount={})",
                activeWorkoutRooms.size(), assigned, totalAmount);
    }

    public PenaltyAccountInfo getPenaltyAccount(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        PenaltyAccount penaltyAccount = penaltyAccountRepository.findByWorkoutRoom(workoutRoom)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        return PenaltyAccountInfo.from(penaltyAccount);
    }

    @Transactional
    public PenaltyAccountInfo upsertPenaltyAccount(Long roomId, PenaltyAccountRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 계좌번호·예금주는 남기지 않는다. 행위자는 MDC memberId.
        log.info("Penalty account upserted (roomId={}, bank={})", roomId, request.getBankName());

        PenaltyAccount penaltyAccount = penaltyAccountRepository.findByWorkoutRoom(workoutRoom)
                .map(existingAccount -> {
                    existingAccount.updateAccountInfo(
                            request.getBankName(),
                            request.getAccountNumber(),
                            request.getAccountHolder()
                    );
                    return existingAccount;
                })
                .orElse(PenaltyAccount.builder()
                        .bankName(request.getBankName())
                        .accountNumber(request.getAccountNumber())
                        .accountHolder(request.getAccountHolder())
                        .workoutRoom(workoutRoom)
                        .build());

        penaltyAccount = penaltyAccountRepository.save(penaltyAccount);
        return PenaltyAccountInfo.from(penaltyAccount);
    }

    @Transactional
    public void deletePenaltyAccount(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        penaltyAccountRepository.findByWorkoutRoom(workoutRoom)
                .ifPresent(penaltyAccountRepository::delete);
        log.info("Penalty account deleted (roomId={})", roomId);
    }

    public List<PenaltyRecord> getPenaltyRecords(Long roomId, LocalDate startDate, LocalDate endDate) {
        List<Penalty> penalties = (startDate != null && endDate != null)
                ? penaltyRepository.findAllByWorkoutRoomIdAndWeekOverlapping(roomId, startDate, endDate)
                : penaltyRepository.findAllByWorkoutRoomId(roomId);
        return penalties.stream()
                .map(PenaltyRecord::from)
                .toList();
    }

    /** @return 이 방에서 새로 부과한 벌금 (요약 로그용) */
    private List<Penalty> processWorkoutRoomPenalties(WorkoutRoom workoutRoom, LocalDate weekStart, LocalDate weekEnd) {
        List<Penalty> assigned = new ArrayList<>();
        log.debug("Processing penalties (roomId={}, name={})", workoutRoom.getId(), workoutRoom.getName());

        // 조회 쿼리가 penaltyEnabled=true 로 걸러 오지만, 다른 경로에서 호출될 때를 대비해 남긴다.
        if (!workoutRoom.getPenaltyEnabled()) {
            log.debug("Penalty disabled, skipping (roomId={})", workoutRoom.getId());
            return assigned;
        }

        List<WorkoutRoomMember> members = workoutRoom.getWorkoutRoomMembers();
        if (members.isEmpty()) {
            log.debug("No members, skipping (roomId={})", workoutRoom.getId());
            return assigned;
        }

        Map<Long, Integer> actualWorkoutsByMemberId = workoutRecordRepository
                .countByWorkoutRoomAndWorkoutDateBetweenGroupByMember(workoutRoom, weekStart, weekEnd)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()
                ));

        List<Rest> restPeriods = restRepository.findAllByWorkoutRoomMemberIn(members);
        Map<Long, List<Rest>> restByWorkoutRoomMemberId = restPeriods.stream()
                .collect(Collectors.groupingBy(rest -> rest.getWorkoutRoomMember().getId()));

        for (WorkoutRoomMember member : members) {
            if (isMemberOnBreak(member, weekStart, weekEnd, restByWorkoutRoomMemberId)) {
                log.debug("Member on break, skipping (roomId={}, memberId={})",
                        workoutRoom.getId(), member.getMember().getId());
                continue;
            }

            int actualWorkouts = actualWorkoutsByMemberId.getOrDefault(member.getMember().getId(), 0);

            int requiredWorkouts = workoutRoom.getMinWeeklyWorkouts();

            if (actualWorkouts < requiredWorkouts) {
                int missedWorkouts = requiredWorkouts - actualWorkouts;
                long penaltyAmount = missedWorkouts * workoutRoom.getPenaltyPerMiss();

                Penalty penalty = Penalty.builder()
                        .workoutRoomMember(member)
                        .penaltyAmount(penaltyAmount)
                        .requiredWorkouts(requiredWorkouts)
                        .actualWorkouts(actualWorkouts)
                        .weekStartDate(weekStart)
                        .weekEndDate(weekEnd)
                        .build();

                penaltyRepository.save(penalty);
                assigned.add(penalty);

                member.updateTotalPenalty(member.getTotalPenalty() + penaltyAmount);

                notifyPenaltyAssigned(member, workoutRoom, penalty);

                // 닉네임은 바뀔 수 있으므로 id 로 남긴다. 금액 분쟁 시 이 줄로 부과 근거를 재구성한다.
                log.info("Penalty assigned (roomId={}, memberId={}, amount={}, required={}, actual={}, week={}~{})",
                        workoutRoom.getId(), member.getMember().getId(), penaltyAmount,
                        requiredWorkouts, actualWorkouts, weekStart, weekEnd);
            } else {
                log.debug("Goal met, no penalty (roomId={}, memberId={}, required={}, actual={})",
                        workoutRoom.getId(), member.getMember().getId(), requiredWorkouts, actualWorkouts);
            }
        }
        return assigned;
    }

    private void notifyPenaltyAssigned(WorkoutRoomMember member, WorkoutRoom workoutRoom, Penalty penalty) {
        String title = "💸 벌금이 부과됐어요";
        String body = String.format("%s에서 이번 주 목표(%d회)를 채우지 못해 벌금 %,d원이 부과됐어요. (인증 %d회)",
                workoutRoom.getName(), penalty.getRequiredWorkouts(), penalty.getPenaltyAmount(), penalty.getActualWorkouts());

        notificationFacade.notifyMember(member.getMember(), title, body, PENALTY_ASSIGNED_TYPE, "");
    }

    private boolean isMemberOnBreak(WorkoutRoomMember member, LocalDate weekStart, LocalDate weekEnd,
                                    Map<Long, List<Rest>> restByWorkoutRoomMemberId) {
        List<Rest> restPeriods = restByWorkoutRoomMemberId.getOrDefault(member.getId(), List.of());

        return restPeriods.stream().anyMatch(rest ->
                !(rest.getEndDate().isBefore(weekStart) || rest.getStartDate().isAfter(weekEnd))
        );
    }
}