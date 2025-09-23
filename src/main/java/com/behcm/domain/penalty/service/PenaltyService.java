package com.behcm.domain.penalty.service;

import com.behcm.domain.penalty.dto.*;
import com.behcm.domain.penalty.entity.BankAccount;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.BankAccountRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PenaltyService {

    private final BankAccountRepository bankAccountRepository;
    private final PenaltyRepository penaltyRepository;
    private final WorkoutRoomRepository workoutRoomRepository;
    private final PaymentService paymentService;

    public BankAccountInfo getBankAccount(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        BankAccount bankAccount = bankAccountRepository.findByWorkoutRoom(workoutRoom)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        return BankAccountInfo.from(bankAccount);
    }

    @Transactional
    public BankAccountInfo upsertBankAccount(Long roomId, BankAccountRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        BankAccount bankAccount = bankAccountRepository.findByWorkoutRoom(workoutRoom)
                .map(existingAccount -> {
                    existingAccount.updateAccountInfo(
                            request.getBankName(),
                            request.getAccountNumber(),
                            request.getHolderName()
                    );
                    return existingAccount;
                })
                .orElse(BankAccount.builder()
                        .bankName(request.getBankName())
                        .accountNumber(request.getAccountNumber())
                        .holderName(request.getHolderName())
                        .workoutRoom(workoutRoom)
                        .build());

        bankAccount = bankAccountRepository.save(bankAccount);
        return BankAccountInfo.from(bankAccount);
    }

    public PenaltyUnpaidSummary getUnpaidPenaltySummary(Long roomId) {
        List<Penalty> unpaidPenalties;

        if (roomId != null) {
            unpaidPenalties = penaltyRepository.findUnpaidByRoomId(roomId);
        } else {
            unpaidPenalties = penaltyRepository.findAllUnpaid();
        }

        List<PenaltyRecord> records = unpaidPenalties.stream()
                .map(PenaltyRecord::from)
                .collect(Collectors.toList());

        return PenaltyUnpaidSummary.from(records);
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