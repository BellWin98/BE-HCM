package com.behcm.domain.admin.workout.service;

import com.behcm.domain.admin.workout.dto.AdminUpdateRoomRequest;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyAccountRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.WorkoutRecordResponse;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomMemberResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorkoutRoomService {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PenaltyAccountRepository penaltyAccountRepository;
    private final PenaltyRepository penaltyRepository;

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
                    List<WorkoutRecordResponse> workoutRecords = workoutRecordRepository.findAllByMemberPerWorkoutDate(workoutRoomMember.getMember()).stream()
                            .map(WorkoutRecordResponse::from)
                            .toList();
                    List<RestResponse> restInfoList = restRepository.findAllByWorkoutRoomMember(workoutRoomMember).stream()
                            .map(RestResponse::from)
                            .toList();
                    return WorkoutRoomMemberResponse.of(workoutRoomMember, workoutRecords, restInfoList);
                })
                .toList();

        return new WorkoutRoomDetailResponse(WorkoutRoomResponse.from(workoutRoom), workoutRoomMembers, null);
    }

    @Transactional
    public WorkoutRoomResponse updateRoomSettings(Long roomId, AdminUpdateRoomRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        workoutRoom.updateRoomSettings(
                workoutRoom.getName(),
                request.getMinWeeklyWorkouts(),
                request.getPenaltyPerMiss(),
                request.getMaxMembers(),
                workoutRoom.getEntryCode()
        );

        WorkoutRoom saved = workoutRoomRepository.save(workoutRoom);
        return WorkoutRoomResponse.from(saved);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        List<WorkoutRoomMember> members = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom);
        for (WorkoutRoomMember wrm : members) {
            List<Rest> rests = restRepository.findAllByWorkoutRoomMember(wrm);
            restRepository.deleteAll(rests);
        }

        workoutRecordRepository.deleteByWorkoutRoom(workoutRoom);

        chatMessageRepository.deleteByWorkoutRoom(workoutRoom);

        List<Penalty> penalties = penaltyRepository.findAllByWorkoutRoomId(workoutRoom.getId());
        penaltyRepository.deleteAll(penalties);

        penaltyAccountRepository.findByWorkoutRoom(workoutRoom)
                .ifPresent(penaltyAccountRepository::delete);

        workoutRoomMemberRepository.deleteAll(members);

        workoutRoomRepository.delete(workoutRoom);
    }
}

