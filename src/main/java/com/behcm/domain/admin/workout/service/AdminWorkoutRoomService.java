package com.behcm.domain.admin.workout.service;

import com.behcm.domain.admin.workout.dto.AdminUpdateRoomRequest;
import com.behcm.domain.chat.dto.ChatHistoryResponse;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.chat.service.ChatService;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyAccountRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.SchedulePenaltyChangeRequest;
import com.behcm.domain.workout.dto.WorkoutRecordResponse;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomMemberResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.domain.workout.service.WorkoutRoomService;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorkoutRoomService {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatService chatService;
    private final PenaltyAccountRepository penaltyAccountRepository;
    private final PenaltyRepository penaltyRepository;
    private final WorkoutRoomService workoutRoomService;

    public Page<WorkoutRoomResponse> getRooms(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = (query != null && !query.isBlank()) ? query : null;
        Page<WorkoutRoom> rooms = workoutRoomRepository.searchAdminRooms(normalizedQuery, active, pageable);
        return rooms.map(WorkoutRoomResponse::from);
    }

    public WorkoutRoomDetailResponse getRoomDetail(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

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
            recordsByMemberId = workoutRecordRepository.findByWorkoutRoomAndMemberInPerWorkoutDate(workoutRoom, memberIds).stream()
                    .collect(Collectors.groupingBy(wr -> wr.getMember().getId()))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(WorkoutRecordResponse::from).toList()));
        } else {
            restByWrmId = Collections.emptyMap();
            recordsByMemberId = Collections.emptyMap();
        }

        List<WorkoutRoomMemberResponse> workoutRoomMembers = members.stream()
                .map(workoutRoomMember -> WorkoutRoomMemberResponse.of(
                        workoutRoomMember,
                        recordsByMemberId.getOrDefault(workoutRoomMember.getMember().getId(), List.of()),
                        restByWrmId.getOrDefault(workoutRoomMember.getId(), List.of())))
                .toList();

        return new WorkoutRoomDetailResponse(WorkoutRoomResponse.from(workoutRoom), workoutRoomMembers, null);
    }

    public ChatHistoryResponse getChatHistory(Long roomId, Long cursorId, int size) {
        return chatService.getChatHistoryForAdmin(roomId, cursorId, size);
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
    public WorkoutRoomResponse schedulePenaltyChange(Long roomId, SchedulePenaltyChangeRequest request) {
        return workoutRoomService.scheduleAdminPenaltyChange(roomId, request);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        List<WorkoutRoomMember> members = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom);
        restRepository.deleteAllByWorkoutRoomMemberIn(members);

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

