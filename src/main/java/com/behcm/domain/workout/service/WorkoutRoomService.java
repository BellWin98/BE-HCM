package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

        // 이미 다른 활성 중인 운동방에 참여 중인지 확인
        if (workoutRoomRepository.findActiveWorkoutRoomByMember(owner).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_WORKOUT_ROOM);
        }

        // 날짜 검증
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "시작 날짜는 오늘 이후여야 합니다.");
        }
        if (request.getEndDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "종료 날짜는 시작 날짜보다 뒤여야 합니다.");
            }
        }

        WorkoutRoom workoutRoom = WorkoutRoom.builder()
                .name(request.getName())
                .minWeeklyWorkouts(request.getMinWeeklyWorkouts())
                .penaltyPerMiss(request.getPenaltyPerMiss())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate() != null ? request.getEndDate() : null)
                .maxMembers(request.getMaxMembers())
                .entryCode(request.getEntryCode())
                .owner(owner)
                .build();

        WorkoutRoom savedWorkoutRoom = workoutRoomRepository.save(workoutRoom);

        // 방장을 멤버로 추가
        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(owner)
                .workoutRoom(savedWorkoutRoom)
                .build();
        workoutRoomMemberRepository.save(workoutRoomMember);

        return WorkoutRoomResponse.from(savedWorkoutRoom);
    }

    @Transactional(readOnly = true)
    public WorkoutRoomDetailResponse getCurrentWorkoutRoom(Member member) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findActiveWorkoutRoomByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "유저가 속한 운동방이 없습니다."));
        List<WorkoutRoomMemberResponse> workoutRoomMembers = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom).stream()
                .map(workoutRoomMember -> {
                    List<WorkoutRecordResponse> workoutRecords = workoutRecordRepository.findAllByMember(workoutRoomMember.getMember()).stream()
                            .map(WorkoutRecordResponse::from)
                            .toList();
                    List<RestResponse> restInfoList = restRepository.findAllByWorkoutRoomMember(workoutRoomMember).stream()
                            .map(RestResponse::from)
                            .toList();
                    return WorkoutRoomMemberResponse.of(workoutRoomMember, workoutRecords, restInfoList);
                })
                .toList();
        Optional<WorkoutRecord> currentMemberWorkoutRecordOpt = workoutRecordRepository.findByMemberAndWorkoutDate(member, LocalDate.now());
        WorkoutRecord currentMemberWorkoutRecord;
        if (currentMemberWorkoutRecordOpt.isPresent()) {
            currentMemberWorkoutRecord = currentMemberWorkoutRecordOpt.get();
            return new WorkoutRoomDetailResponse(WorkoutRoomResponse.from(workoutRoom), workoutRoomMembers, WorkoutRecordResponse.from(currentMemberWorkoutRecord));
        }
        return new WorkoutRoomDetailResponse(WorkoutRoomResponse.from(workoutRoom), workoutRoomMembers, null);


    }

    @Transactional(readOnly = true)
    public List<WorkoutRoomResponse> getWorkoutRooms() {
        return workoutRoomRepository.findActiveRooms().stream()
                .map(WorkoutRoomResponse::from)
                .toList();
    }

    public WorkoutRoomResponse joinWorkoutRoom(Long workoutRoomId, String entryCode, Member member) {

        WorkoutRoom workoutRoom = workoutRoomRepository.findById(workoutRoomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        // 다른 운동방에 참여 중인지 확인
        if (workoutRoomRepository.findActiveWorkoutRoomByMember(member).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_WORKOUT_ROOM);
        }

        if (!workoutRoom.getEntryCode().equals(entryCode)) {
            throw new CustomException(ErrorCode.INVALID_ENTRY_CODE);
        }

        if (!workoutRoom.canJoin()) {
            if (!workoutRoom.getIsActive()) {
                throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "비활성화된 방입니다.");
            } else if (workoutRoom.getCurrentMemberCount() >= workoutRoom.getMaxMembers()) {
                throw new CustomException(ErrorCode.WORKOUT_ROOM_FULL);
            }
        }

        // 이미 해당 운동방에 참여중인지 확인
        if (workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(member, workoutRoom)) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_WORKOUT_ROOM, "이미 동일한 운동방에 참여 중입니다.");
        }

        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(member)
                .workoutRoom(workoutRoom)
                .build();
        workoutRoomMemberRepository.save(workoutRoomMember);

        return WorkoutRoomResponse.from(workoutRoom);
    }

    @Transactional(readOnly = true)
    public boolean isMemberInWorkoutRoom(Member member) {
        return workoutRoomRepository.findActiveWorkoutRoomByMember(member).isPresent();
    }
}
