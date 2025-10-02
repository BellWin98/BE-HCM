package com.behcm.domain.penalty.service;

import com.behcm.domain.penalty.dto.*;
import com.behcm.domain.penalty.entity.PenaltyAccount;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyAccountRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.behcm.global.util.DateUtils.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PenaltyService {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final PenaltyAccountRepository penaltyAccountRepository;
    private final PenaltyRepository penaltyRepository;
    private final PenaltyCalculator penaltyCalculator;

    @Transactional
    public void calculateAndAssignPenalties() {
        log.info("Starting weekly penalty calculation");

        LocalDate lastWeekStart = getLastWeekStart();
        LocalDate lastWeekEnd = getLastWeekEnd();

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


    public List<PenaltyRecord> getPenaltyRecords(Long roomId) {
        return penaltyRepository.findAllByWorkoutRoomId(roomId).stream()
                .map(PenaltyRecord::from)
                .toList();
    }

    private void processWorkoutRoomPenalties(WorkoutRoom workoutRoom, LocalDate weekStart, LocalDate weekEnd) {
        log.info("Processing penalties for workout room: {} (ID: {})", workoutRoom.getName(), workoutRoom.getId());

        List<WorkoutRoomMember> members = workoutRoom.getWorkoutRoomMembers();

        for (WorkoutRoomMember member : members) {
            Penalty penalty = penaltyCalculator.calculatePenalty(member, workoutRoom, weekStart, weekEnd);

            if (penalty != null) {
                penaltyRepository.save(penalty);
                member.updateTotalPenalty(member.getTotalPenalty() + penalty.getPenaltyAmount());
            }
        }
    }
}