package com.behcm.domain.chat.repository;

import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 채팅방 ID를 기준으로 메시지를 최신순으로 페이징하여 조회합니다.
    Page<ChatMessage> findByWorkoutRoomOrderByTimestampDesc(WorkoutRoom workoutRoom, Pageable pageable);
}
