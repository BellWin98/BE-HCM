package com.behcm.domain.admin.workout.service;

import com.behcm.domain.admin.workout.dto.AdminUpdateRoomRequest;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.WorkoutRecordResponse;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomMemberResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorkoutRoomService {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;

    public Page<WorkoutRoomResponse> getRooms(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = (query != null && !query.isBlank()) ? query : null;
        Page<WorkoutRoom> rooms = workoutRoomRepository.searchAdminRooms(normalizedQuery, active, pageable);
        return rooms.map(WorkoutRoomResponse::from);
    }

    public WorkoutRoomDetailResponse getRoomDetail(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

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

        // Admin 조회에서는 특정 "현재 회원" 개념이 없으므로 오늘 운동 기록은 null로 반환
        return new WorkoutRoomDetailResponse(WorkoutRoomResponse.from(workoutRoom), workoutRoomMembers, null);
    }

    @Transactional
    public WorkoutRoomResponse updateRoomSettings(Long roomId, AdminUpdateRoomRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        if (startDate.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "시작 날짜는 오늘 이후여야 합니다.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "종료 날짜는 시작 날짜보다 뒤여야 합니다.");
        }

        workoutRoom.updateRoomSettings(
                workoutRoom.getName(),
                request.getMinWeeklyWorkouts(),
                request.getPenaltyPerMiss(),
                startDate,
                endDate,
                request.getMaxMembers(),
                workoutRoom.getEntryCode()
        );

        WorkoutRoom saved = workoutRoomRepository.save(workoutRoom);
        return WorkoutRoomResponse.from(saved);
    }
}

