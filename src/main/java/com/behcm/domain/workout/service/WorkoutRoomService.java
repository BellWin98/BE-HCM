package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.workout.dto.CreateWorkoutRoomRequest;
import com.behcm.domain.workout.dto.WorkoutRoomMemberResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
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
    private final MemberRepository memberRepository;

    public WorkoutRoomResponse createWorkoutRoom(Member owner, CreateWorkoutRoomRequest request) {

        // 이미 다른 활성 중인 운동방에 참여 중인지 확인
        if (workoutRoomRepository.findActiveWorkoutRoomByMember(owner).isPresent()) {
            throw new CustomException(ErrorCode.ALREADY_JOINED_ROOM);
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
    public WorkoutRoomResponse getCurrentWorkoutRoom(Member member) {
        Optional<WorkoutRoom> workoutRoomOpt = workoutRoomRepository.findActiveWorkoutRoomByMember(member);
        if (workoutRoomOpt.isPresent()) {
            WorkoutRoom workoutRoom = workoutRoomOpt.get();
            List<WorkoutRoomMemberResponse> workoutRoomMembers = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom).stream()
                    .map(WorkoutRoomMemberResponse::from)
                    .toList();
            return WorkoutRoomResponse.from(workoutRoom, workoutRoomMembers);
        }
        return null;
    }

    public List<WorkoutRoomResponse> getWorkoutRooms() {
        return workoutRoomRepository.findActiveRooms().stream()
                .map(WorkoutRoomResponse::from)
                .toList();
    }
}
