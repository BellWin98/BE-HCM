package com.behcm.domain.chat.service;

import com.behcm.domain.chat.dto.ChatMessageRequest;
import com.behcm.domain.chat.dto.ChatMessageResponse;
import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public void sendMessage(Long roomId, Member sender, ChatMessageRequest request) {
        WorkoutRoomMember workoutRoomSender = workoutRoomMemberRepository.findByMember(sender)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));
        if (!roomId.equals(workoutRoomSender.getWorkoutRoom().getId())) {
            throw new IllegalArgumentException("roomId와 실제 운동방 id 불일치");
        }
        ChatMessage chatMessage = ChatMessage.builder()
                .sender(sender)
                .workoutRoom(workoutRoomSender.getWorkoutRoom())
                .content(request.getContent())
                .messageType(request.getType())
                .build();

        // 보낸 사람은 자동으로 읽음 처리
        chatMessage.addReadBy(workoutRoomSender.getNickname());

        ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);
        ChatMessageResponse response = ChatMessageResponse.from(savedChatMessage);

        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId, response);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Member member, Long roomId, int page, int size) {
        WorkoutRoomMember workoutRoomMember = workoutRoomMemberRepository.findByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));
        WorkoutRoom workoutRoom = workoutRoomMember.getWorkoutRoom();
        if (!roomId.equals(workoutRoom.getId())) {
            throw new IllegalArgumentException("roomId와 실제 운동방 id 불일치");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> chatMessages = chatMessageRepository.findByWorkoutRoomOrderByTimestampAsc(workoutRoom, pageable);

        return chatMessages.getContent().stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    public void markAsRead(Long roomId, Long messageId, Member member) {
        WorkoutRoomMember workoutRoomMember = workoutRoomMemberRepository.findByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));
        if (!roomId.equals(workoutRoomMember.getWorkoutRoom().getId())) {
            throw new IllegalArgumentException("roomId와 실제 운동방 id 불일치");
        }
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        chatMessage.addReadBy(workoutRoomMember.getNickname());
        chatMessageRepository.save(chatMessage);

        // 읽음 상태 업데이트 정보를 브로드캐스팅
        ChatMessageResponse response = ChatMessageResponse.from(chatMessage);
        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId + "/read", response);
    }
}