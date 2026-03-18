package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.*;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkoutRoomService {

    private static final int ENTRY_CODE_LENGTH = 8;
    private static final int ENTRY_CODE_MAX_GENERATION_ATTEMPTS = 30;
    private static final char[] ENTRY_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_JOINED_WORKOUT_ROOMS = 3;

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;

    public WorkoutRoomResponse createWorkoutRoom(Member owner, CreateWorkoutRoomRequest request) {
        validateWorkoutRoomLimit(owner);

        WorkoutRoom workoutRoom = WorkoutRoom.builder()
                .name(request.getName())
                .minWeeklyWorkouts(request.getMinWeeklyWorkouts())
                .penaltyPerMiss(request.getPenaltyPerMiss())
                .maxMembers(request.getMaxMembers())
                .entryCode(request.getEntryCode())
                .owner(owner)
                .build();

        WorkoutRoom savedWorkoutRoom = workoutRoomRepository.save(workoutRoom);

        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(owner)
                .workoutRoom(savedWorkoutRoom)
                .build();
        workoutRoomMemberRepository.save(workoutRoomMember);

        return WorkoutRoomResponse.from(savedWorkoutRoom);
    }

    @Transactional(readOnly = true)
    public List<WorkoutRoomResponse> getJoinedWorkoutRooms(Member member) {
        return workoutRoomMemberRepository.findByMember(member).stream()
                .map(workoutRoomMember -> WorkoutRoomResponse.from(workoutRoomMember.getWorkoutRoom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutRoomDetailResponse getJoinedWorkoutRoom(Long roomId, Member member) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "유저가 속한 운동방이 없습니다."));
        List<WorkoutRoomMember> members = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(workoutRoom);

        final Map<Long, List<RestResponse>> restByWrmId;
        final Map<Long, List<WorkoutRecordResponse>> recordsByMemberId;
        if (!members.isEmpty()) {
            List<Long> memberIds = members.stream()
                    .map(wrm -> wrm.getMember().getId())
                    .toList();
            restByWrmId = restRepository.findAllByWorkoutRoomMemberIn(members).stream()
                    .collect(Collectors.groupingBy(r -> r.getWorkoutRoomMember().getId()))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(RestResponse::from).toList()));
            recordsByMemberId = workoutRecordRepository.findByWorkoutRoomAndMemberIn(workoutRoom, memberIds).stream()
                    .collect(Collectors.groupingBy(wr -> wr.getMember().getId()))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(WorkoutRecordResponse::from).toList()));
        } else {
            restByWrmId = Collections.emptyMap();
            recordsByMemberId = Collections.emptyMap();
        }

        List<WorkoutRoomMemberResponse> workoutRoomMembers = members.stream()
                .map(workoutRoomMember -> {
                    List<WorkoutRecordResponse> workoutRecords = recordsByMemberId.getOrDefault(workoutRoomMember.getMember().getId(), List.of());
                    List<RestResponse> restInfoList = restByWrmId.getOrDefault(workoutRoomMember.getId(), List.of());
                    return WorkoutRoomMemberResponse.of(workoutRoomMember, workoutRecords, restInfoList);
                })
                .toList();

        Optional<WorkoutRecord> currentMemberWorkoutRecordOpt = workoutRecordRepository.findByMemberAndWorkoutRoomAndWorkoutDate(member, workoutRoom, LocalDate.now());
        return currentMemberWorkoutRecordOpt.map(workoutRecord -> new WorkoutRoomDetailResponse(
                WorkoutRoomResponse.from(workoutRoom),
                workoutRoomMembers,
                WorkoutRecordResponse.from(workoutRecord))).orElseGet(() -> new WorkoutRoomDetailResponse(WorkoutRoomResponse.from(workoutRoom), workoutRoomMembers, null));
    }

    @Transactional(readOnly = true)
    public List<WorkoutRoomResponse> getWorkoutRooms() {
        return workoutRoomRepository.findActiveRooms().stream()
                .map(WorkoutRoomResponse::fromWithoutEntryCode)
                .toList();
    }

    public WorkoutRoomResponse joinWorkoutRoomByCode(String entryCode, Member member) {
        validateWorkoutRoomLimit(member);
        WorkoutRoom workoutRoom = workoutRoomRepository.findByEntryCodeAndIsActiveTrue(entryCode)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        if (!workoutRoom.canJoin()) {
            if (!workoutRoom.getIsActive()) {
                throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "비활성화된 방입니다.");
            }
            if (workoutRoom.getCurrentMemberCount() >= workoutRoom.getMaxMembers()) {
                throw new CustomException(ErrorCode.WORKOUT_ROOM_FULL);
            }
        }

        if (workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(member, workoutRoom)) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_THIS_WORKOUT_ROOM);
        }

        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(member)
                .workoutRoom(workoutRoom)
                .build();
        workoutRoomMemberRepository.save(workoutRoomMember);

        return WorkoutRoomResponse.from(workoutRoom);
    }

    public WorkoutRoomResponse regenerateEntryCode(Long roomId, Member currentMember) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        if (!workoutRoom.isOwner(currentMember)) {
            throw new CustomException(ErrorCode.NOT_WORKOUT_ROOM_OWNER);
        }

        String newEntryCode = generateUniqueEntryCode(workoutRoom.getEntryCode());
        workoutRoom.updateEntryCode(newEntryCode);

        return WorkoutRoomResponse.from(workoutRoom);
    }

    private String generateUniqueEntryCode(String currentEntryCode) {
        for (int attempt = 0; attempt < ENTRY_CODE_MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generateEntryCode();
            if (candidate.equals(currentEntryCode)) {
                continue;
            }
            if (!workoutRoomRepository.existsByEntryCode(candidate)) {
                return candidate;
            }
        }
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "입장 코드 생성에 실패했습니다.");
    }

    private String generateEntryCode() {
        char[] buf = new char[ENTRY_CODE_LENGTH];
        for (int i = 0; i < ENTRY_CODE_LENGTH; i++) {
            buf[i] = ENTRY_CODE_CHARS[SECURE_RANDOM.nextInt(ENTRY_CODE_CHARS.length)];
        }
        return new String(buf);
    }

    private void validateWorkoutRoomLimit(Member member) {
        if (member.getRole() == MemberRole.ADMIN) {
            return;
        }

        long joinedRoomCount = workoutRoomMemberRepository.countByMember(member);
        if (joinedRoomCount >= MAX_JOINED_WORKOUT_ROOMS) {
            throw new CustomException(ErrorCode.COUNT_LIMIT_EXCEEDED, "일반 회원은 최대 3개의 운동방에만 참여할 수 있습니다.");
        }
    }
}
