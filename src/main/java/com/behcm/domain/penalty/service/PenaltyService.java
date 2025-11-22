package com.behcm.domain.penalty.service;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PenaltyService {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;
    private final PenaltyAccountRepository penaltyAccountRepository;
    private final PenaltyRepository penaltyRepository;
    private final PaymentService paymentService;

    @Transactional
    public void calculateAndAssignPenalties() {
        log.info("Starting weekly penalty calculation");

        LocalDate today = LocalDate.now();
        LocalDate lastWeekStart = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

        log.info("Calculating penalties for week: {} to {}", lastWeekStart, lastWeekEnd);

        List<WorkoutRoom> activeWorkoutRooms = workoutRoomRepository.findByIsActiveTrue();

        for (WorkoutRoom workoutRoom : activeWorkoutRooms) {
            processWorkoutRoomPenalties(workoutRoom, lastWeekStart, lastWeekEnd);
        }

        log.info("Weekly penalty calculation completed");
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
    }

//    public PenaltyUnpaidSummary getPenaltyRecords(Long roomId) {
//        List<Penalty> unpaidPenalties;
//
//        if (roomId != null) {
//            unpaidPenalties = penaltyRepository.findUnpaidByRoomId(roomId);
//        } else {
//            unpaidPenalties = penaltyRepository.findAllUnpaid();
//        }
//
//        List<PenaltyRecord> records = unpaidPenalties.stream()
//                .map(PenaltyRecord::from)
//                .collect(Collectors.toList());
//
//        return PenaltyUnpaidSummary.from(records);
//    }

    public List<PenaltyRecord> getPenaltyRecords(Long roomId) {
        return penaltyRepository.findAllByWorkoutRoomId(roomId).stream()
                .map(PenaltyRecord::from)
                .toList();
    }

    @Transactional
    public PayPenaltyResponse payPenalty(Long roomId, PayPenaltyRequest request) {
        List<Penalty> penaltiesToPay = getPenaltiesToPay(roomId, request);

        Long totalPenaltyAmount = penaltiesToPay.stream()
                .mapToLong(Penalty::getPenaltyAmount)
                .sum();

        if (!request.getAmount().equals(totalPenaltyAmount)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        String orderId = generateOrderId();
        PaymentService.PaymentResult paymentResult = paymentService.processPayment(request.getAmount(), orderId);

        if (!paymentResult.isSuccess()) {
            throw new CustomException(ErrorCode.PAYMENT_FAILED);
        }

        penaltiesToPay.forEach(Penalty::markAsPaid);
        penaltyRepository.saveAll(penaltiesToPay);

        return PayPenaltyResponse.builder()
                .success(true)
                .paidAmount(request.getAmount())
                .paidAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    private void processWorkoutRoomPenalties(WorkoutRoom workoutRoom, LocalDate weekStart, LocalDate weekEnd) {
        log.info("Processing penalties for workout room: {} (ID: {})", workoutRoom.getName(), workoutRoom.getId());

        List<WorkoutRoomMember> members = workoutRoom.getWorkoutRoomMembers();

        for (WorkoutRoomMember member : members) {
            if (isMemberOnBreak(member, weekStart, weekEnd)) {
                log.debug("Skipping penalty calculation for member {} - on break", member.getNickname());
                continue;
            }

            int actualWorkouts = (int) workoutRecordRepository.countByMemberAndWorkoutRoomAndWorkoutDateBetween(
                    member.getMember(), workoutRoom, weekStart, weekEnd);

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

                member.updateTotalPenalty(member.getTotalPenalty() + penaltyAmount);

                log.info("Penalty assigned to {} in room {}: {}원 (Required: {}, Actual: {})",
                        member.getNickname(), workoutRoom.getName(), penaltyAmount, requiredWorkouts, actualWorkouts);
            } else {
                log.debug("No penalty for {} in room {} - met requirements (Required: {}, Actual: {})",
                        member.getNickname(), workoutRoom.getName(), requiredWorkouts, actualWorkouts);
            }
        }
    }

    private boolean isMemberOnBreak(WorkoutRoomMember member, LocalDate weekStart, LocalDate weekEnd) {
        List<Rest> restPeriods = restRepository.findAllByWorkoutRoomMember(member);

        return restPeriods.stream().anyMatch(rest ->
                !(rest.getEndDate().isBefore(weekStart) || rest.getStartDate().isAfter(weekEnd))
        );
    }

    private List<Penalty> getPenaltiesToPay(Long roomId, PayPenaltyRequest request) {
        if (request.getPenaltyRecordIds() != null && !request.getPenaltyRecordIds().isEmpty()) {
            return penaltyRepository.findByIds(request.getPenaltyRecordIds());
        } else if (roomId != null) {
            return penaltyRepository.findUnpaidByRoomId(roomId);
        } else {
            return penaltyRepository.findAllUnpaid();
        }
    }

    private String generateOrderId() {
        return "PEN_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}