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

        // 이미 다른 활성 중인 운동방에 참여 중인지 확인 (관리자는 제외)
        if (owner.getRole() == MemberRole.USER
                && workoutRoomRepository.findActiveWorkoutRoomByMember(owner).isPresent()) {
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
        // NOTE: ADMIN이 여러 운동방에 참여한 경우, 첫 번째 방만 반환됩니다.
        // 모든 참여 방을 조회하려면 getJoinedWorkoutRooms() 메서드를 사용하세요.
        WorkoutRoom workoutRoom = workoutRoomRepository.findFirstByWorkoutRoomMembersMemberAndIsActiveTrue(member)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "유저가 속한 운동방이 없습니다."));

        List<WorkoutRoomMemberResponse> workoutRoomMembers = buildWorkoutRoomMembers(workoutRoom);
        Optional<WorkoutRecordResponse> currentMemberWorkoutRecord = getCurrentMemberTodayWorkoutRecord(member,
                workoutRoom);

        return buildWorkoutRoomDetailResponse(workoutRoom, workoutRoomMembers, currentMemberWorkoutRecord);
    }

    @Transactional(readOnly = true)
    public List<WorkoutRoomResponse> getJoinedWorkoutRooms(Member member) {
        return workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member).stream()
                .map(workoutRoomMember -> WorkoutRoomResponse.from(workoutRoomMember.getWorkoutRoom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkoutRoomDetailResponse getJoinedWorkoutRoom(Long roomId, Member member) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "유저가 속한 운동방이 없습니다."));

        List<WorkoutRoomMemberResponse> workoutRoomMembers = buildWorkoutRoomMembers(workoutRoom);
        Optional<WorkoutRecordResponse> currentMemberWorkoutRecord = getCurrentMemberTodayWorkoutRecord(member,
                workoutRoom);

        return buildWorkoutRoomDetailResponse(workoutRoom, workoutRoomMembers, currentMemberWorkoutRecord);
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

        // 다른 운동방에 참여 중인지 확인 (관리자의 경우, 여러 운동방에 참여 가능)
        if (member.getRole() == MemberRole.USER
                && workoutRoomRepository.findActiveWorkoutRoomByMember(member).isPresent()) {
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
        return !workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member).isEmpty();
    }

    /**
     * 운동방의 모든 멤버와 각 멤버의 운동 기록, 휴식 정보를 조회하여 응답 리스트를 생성합니다.
     *
     * @param workoutRoom 운동방 엔티티
     * @return 운동방 멤버 응답 리스트
     */
    private List<WorkoutRoomMemberResponse> buildWorkoutRoomMembers(WorkoutRoom workoutRoom) {
        return workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom).stream()
                .map(workoutRoomMember -> {
                    List<WorkoutRecordResponse> workoutRecords = workoutRecordRepository
                            .findAllByMember(workoutRoomMember.getMember()).stream()
                            .map(WorkoutRecordResponse::from)
                            .toList();
                    List<RestResponse> restInfoList = restRepository.findAllByWorkoutRoomMember(workoutRoomMember)
                            .stream()
                            .map(RestResponse::from)
                            .toList();
                    return WorkoutRoomMemberResponse.of(workoutRoomMember, workoutRecords, restInfoList);
                })
                .toList();
    }

    /**
     * 현재 멤버의 오늘 날짜 운동 기록을 조회합니다.
     *
     * @param member      멤버 엔티티
     * @param workoutRoom 운동방 엔티티
     * @return 오늘 날짜의 운동 기록 응답 (없으면 빈 Optional)
     */
    private Optional<WorkoutRecordResponse> getCurrentMemberTodayWorkoutRecord(Member member, WorkoutRoom workoutRoom) {
        return workoutRecordRepository.findByMemberAndWorkoutRoomAndWorkoutDate(member, workoutRoom, LocalDate.now())
                .map(WorkoutRecordResponse::from);
    }

    /**
     * 운동방 상세 응답 객체를 생성합니다.
     *
     * @param workoutRoom   운동방 엔티티
     * @param members       운동방 멤버 응답 리스트
     * @param currentRecord 현재 멤버의 오늘 운동 기록 응답 (Optional)
     * @return 운동방 상세 응답 객체
     */
    private WorkoutRoomDetailResponse buildWorkoutRoomDetailResponse(
            WorkoutRoom workoutRoom,
            List<WorkoutRoomMemberResponse> members,
            Optional<WorkoutRecordResponse> currentRecord) {
        WorkoutRecordResponse currentMemberWorkoutRecord = currentRecord.orElse(null);
        return new WorkoutRoomDetailResponse(
                WorkoutRoomResponse.from(workoutRoom),
                members,
                currentMemberWorkoutRecord);
    }
}
