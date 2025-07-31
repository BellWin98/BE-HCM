package com.behcm.domain.chat.repository;

import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 채팅방 ID를 기준으로 메시지를 최신순으로 페이징하여 조회합니다.
    // Page<ChatMessage> findByWorkoutRoomOrderByTimestampAsc(WorkoutRoom workoutRoom, Pageable pageable);

    /* Slice를 사용해 다음 페이지 존재 여부를 효율적으로 확인 */

    // 커서 ID를 기준으로 이전 메시지들을 Slice로 내림차순(최신순)으로 조회
    Slice<ChatMessage> findByWorkoutRoomAndIdLessThanOrderByIdDesc(WorkoutRoom workoutRoom, Long id, Pageable pageable);

    // 특정 채팅방의 가장 최신 메시지들을 조회
    Slice<ChatMessage> findByWorkoutRoomOrderByIdDesc(WorkoutRoom workoutRoom, Pageable pageable);

    // 마지막으로 읽은 메시지 이후의 모든 새 메시지를 조회
    List<ChatMessage> findByWorkoutRoomAndIdGreaterThanOrderByIdAsc(WorkoutRoom workoutRoom, Long id);

    // 특정 채팅방의 가장 최근 메시지 1개를 조회
    ChatMessage findFirstByWorkoutRoomOrderByIdDesc(WorkoutRoom workoutRoom);
}
