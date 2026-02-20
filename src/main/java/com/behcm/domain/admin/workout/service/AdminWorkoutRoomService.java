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
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutLikeRepository;
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
    private final WorkoutLikeRepository workoutLikeRepository;

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

        workoutRoom.updateRoomSettings(
                workoutRoom.getName(),
                request.getMinWeeklyWorkouts(),
                request.getPenaltyPerMiss(),
                workoutRoom.getStartDate(),
                workoutRoom.getEndDate(),
                request.getMaxMembers(),
                workoutRoom.getEntryCode()
        );

        WorkoutRoom saved = workoutRoomRepository.save(workoutRoom);
        return WorkoutRoomResponse.from(saved);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        // 삭제 대상 운동방 조회
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        // WorkoutRoomMember의 Rest 삭제
        List<WorkoutRoomMember> members = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom);
        for (WorkoutRoomMember wrm : members) {
            List<Rest> rests = restRepository.findAllByWorkoutRoomMember(wrm);
            restRepository.deleteAll(rests);
        }

        // WorkoutLike 삭제 (운동방의 운동 기록에 대한 좋아요 - WorkoutRecord 삭제 전에 처리)
        List<WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByWorkoutRoom(workoutRoom);
        for (WorkoutRecord wr : workoutRecords) {
            workoutLikeRepository.deleteByWorkoutRecord(wr);
        }

        // WorkoutRecord 삭제
        workoutRecordRepository.deleteByWorkoutRoom(workoutRoom);

        // ChatMessage 삭제
        chatMessageRepository.deleteByWorkoutRoom(workoutRoom);

        // Penalty 삭제
        List<Penalty> penalties = penaltyRepository.findAllByWorkoutRoomId(workoutRoom.getId());
        penaltyRepository.deleteAll(penalties);

        // PenaltyAccount 삭제
        penaltyAccountRepository.findByWorkoutRoom(workoutRoom)
                .ifPresent(penaltyAccountRepository::delete);

        // WorkoutRoomMember는 CASCADE로 자동 삭제되지만 명시적으로 처리
        workoutRoomMemberRepository.deleteAll(members);

        // 운동방 삭제
        workoutRoomRepository.delete(workoutRoom);
    }
}

