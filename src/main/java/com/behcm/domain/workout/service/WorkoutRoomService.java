package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.*;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.global.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkoutRoomService {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final MemberRepository memberRepository;
    private final RestRepository restRepository;

    public WorkoutRoomResponse createWorkoutRoom(Member owner, CreateWorkoutRoomRequest request) {
        log.info("Creating workout room '{}' for owner: {}", request.getName(), owner.getEmail());

        validateOwnerCanCreateRoom(owner);
        ValidationUtils.validateDateRange(request.getStartDate(), request.getEndDate());

        WorkoutRoom workoutRoom = buildWorkoutRoom(request, owner);
        WorkoutRoom savedWorkoutRoom = workoutRoomRepository.save(workoutRoom);

        addOwnerAsMember(owner, savedWorkoutRoom);

        log.info("Successfully created workout room: {} (ID: {})", savedWorkoutRoom.getName(), savedWorkoutRoom.getId());
        return WorkoutRoomResponse.from(savedWorkoutRoom);
    }

    @Transactional(readOnly = true)
    public WorkoutRoomDetailResponse getCurrentWorkoutRoom(Member member) {
        // NOTE: ADMIN이 여러 운동방에 참여한 경우, 첫 번째 방만 반환됩니다.
        // 모든 참여 방을 조회하려면 getJoinedWorkoutRooms() 메서드를 사용하세요.
        WorkoutRoom workoutRoom = findCurrentWorkoutRoomByMember(member);

        return buildWorkoutRoomDetailResponse(workoutRoom, member);
    }

    @Cacheable(value = "memberRooms", key = "#member.id")
    @Transactional(readOnly = true)
    public List<WorkoutRoomResponse> getJoinedWorkoutRooms(Member member) {
        return workoutRoomMemberRepository.findWorkoutRoomMembersByMemberWithFetch(member).stream()
                .map(workoutRoomMember -> WorkoutRoomResponse.from(workoutRoomMember.getWorkoutRoom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutRoomDetailResponse getJoinedWorkoutRoom(Long roomId, Member member) {
        WorkoutRoom workoutRoom = findWorkoutRoomById(roomId);

        return buildWorkoutRoomDetailResponse(workoutRoom, member);
    }

    @Cacheable(value = "activeRooms")
    @Transactional(readOnly = true)
    public List<WorkoutRoomResponse> getAllActiveWorkoutRooms() {
        return workoutRoomRepository.findActiveRooms().stream()
                .map(WorkoutRoomResponse::from)
                .toList();
    }

    @CacheEvict(value = {"memberRooms", "activeRooms"}, allEntries = true)
    public WorkoutRoomResponse joinWorkoutRoom(Long workoutRoomId, String entryCode, Member member) {
        log.info("Member {} attempting to join workout room ID: {}", member.getEmail(), workoutRoomId);

        WorkoutRoom workoutRoom = findWorkoutRoomById(workoutRoomId);

        validateMemberCanJoinRoom(member, workoutRoom, entryCode);

        addMemberToRoom(member, workoutRoom);

        log.info("Member {} successfully joined workout room: {} (ID: {})",
                member.getEmail(), workoutRoom.getName(), workoutRoom.getId());
        return WorkoutRoomResponse.from(workoutRoom);
    }

    @Transactional(readOnly = true)
    public boolean isMemberInWorkoutRoom(Member member) {
        return !workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member).isEmpty();
    }

    // Private helper methods for validation and business logic

    private void validateOwnerCanCreateRoom(Member owner) {
        if (owner.getRole() == MemberRole.USER &&
            workoutRoomRepository.findActiveWorkoutRoomByMember(owner).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_WORKOUT_ROOM);
        }
    }

    private WorkoutRoom buildWorkoutRoom(CreateWorkoutRoomRequest request, Member owner) {
        return WorkoutRoom.builder()
                .name(request.getName())
                .minWeeklyWorkouts(request.getMinWeeklyWorkouts())
                .penaltyPerMiss(request.getPenaltyPerMiss())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .maxMembers(request.getMaxMembers())
                .entryCode(request.getEntryCode())
                .owner(owner)
                .build();
    }

    private void addOwnerAsMember(Member owner, WorkoutRoom workoutRoom) {
        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(owner)
                .workoutRoom(workoutRoom)
                .build();
        workoutRoomMemberRepository.save(workoutRoomMember);
    }

    private WorkoutRoom findWorkoutRoomById(Long workoutRoomId) {
        return workoutRoomRepository.findById(workoutRoomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));
    }

    private WorkoutRoom findCurrentWorkoutRoomByMember(Member member) {
        return workoutRoomRepository.findFirstByWorkoutRoomMembersMemberAndIsActiveTrue(member)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "유저가 속한 운동방이 없습니다."));
    }

    private void validateMemberCanJoinRoom(Member member, WorkoutRoom workoutRoom, String entryCode) {
        validateMemberNotInOtherRoom(member);
        validateEntryCode(workoutRoom, entryCode);
        validateRoomCanAcceptMembers(workoutRoom);
        validateMemberNotAlreadyInRoom(member, workoutRoom);
    }

    private void validateMemberNotInOtherRoom(Member member) {
        if (member.getRole() == MemberRole.USER &&
            workoutRoomRepository.findActiveWorkoutRoomByMember(member).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_WORKOUT_ROOM);
        }
    }

    private void validateEntryCode(WorkoutRoom workoutRoom, String entryCode) {
        if (!workoutRoom.getEntryCode().equals(entryCode)) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_CODE);
        }
    }

    private void validateRoomCanAcceptMembers(WorkoutRoom workoutRoom) {
        if (!workoutRoom.canJoin()) {
            if (!workoutRoom.getIsActive()) {
                throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "비활성화된 방입니다.");
            } else if (workoutRoom.getCurrentMemberCount() >= workoutRoom.getMaxMembers()) {
                throw new CustomException(ErrorCode.WORKOUT_ROOM_FULL);
            }
        }
    }

    private void validateMemberNotAlreadyInRoom(Member member, WorkoutRoom workoutRoom) {
        if (workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(member, workoutRoom)) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_WORKOUT_ROOM, "이미 동일한 운동방에 참여 중입니다.");
        }
    }

    private void addMemberToRoom(Member member, WorkoutRoom workoutRoom) {
        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(member)
                .workoutRoom(workoutRoom)
                .build();
        workoutRoomMemberRepository.save(workoutRoomMember);
    }

    private WorkoutRoomDetailResponse buildWorkoutRoomDetailResponse(WorkoutRoom workoutRoom, Member member) {
        List<WorkoutRoomMemberResponse> workoutRoomMembers = buildWorkoutRoomMemberResponses(workoutRoom);
        WorkoutRecordResponse currentMemberWorkoutRecord = getCurrentMemberWorkoutRecord(member, workoutRoom);

        return new WorkoutRoomDetailResponse(
                WorkoutRoomResponse.from(workoutRoom),
                workoutRoomMembers,
                currentMemberWorkoutRecord
        );
    }

    private List<WorkoutRoomMemberResponse> buildWorkoutRoomMemberResponses(WorkoutRoom workoutRoom) {
        return workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom).stream()
                .map(this::buildWorkoutRoomMemberResponse)
                .toList();
    }

    private WorkoutRoomMemberResponse buildWorkoutRoomMemberResponse(WorkoutRoomMember workoutRoomMember) {
        List<WorkoutRecordResponse> workoutRecords = workoutRecordRepository
                .findAllByMember(workoutRoomMember.getMember()).stream()
                .map(WorkoutRecordResponse::from)
                .toList();

        List<RestResponse> restInfoList = restRepository
                .findAllByWorkoutRoomMember(workoutRoomMember).stream()
                .map(RestResponse::from)
                .toList();

        return WorkoutRoomMemberResponse.of(workoutRoomMember, workoutRecords, restInfoList);
    }

    private WorkoutRecordResponse getCurrentMemberWorkoutRecord(Member member, WorkoutRoom workoutRoom) {
        return workoutRecordRepository
                .findByMemberAndWorkoutRoomAndWorkoutDate(member, workoutRoom, LocalDate.now())
                .map(WorkoutRecordResponse::from)
                .orElse(null);
    }
}
