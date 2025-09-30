package com.behcm.domain.chat.service;

import com.behcm.domain.chat.dto.ChatHistoryResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
        WorkoutRoomMember wrm = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(sender).stream()
                .filter(workoutRoomMember -> workoutRoomMember.getWorkoutRoom().getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        ChatMessage chatMessage = ChatMessage.builder()
                .sender(sender)
                .workoutRoom(wrm.getWorkoutRoom())
                .content(request.getContent())
                .messageType(request.getType())
                .build();

        // 보낸 사람은 자동으로 읽음 처리
        chatMessage.addReadBy(wrm.getNickname());

        ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);
        ChatMessageResponse response = ChatMessageResponse.from(savedChatMessage);

        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId, response);
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(Member member, Long roomId, Long cursorId, int size) {
        WorkoutRoomMember wrm = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member).stream()
                .filter(workoutRoomMember -> workoutRoomMember.getWorkoutRoom().getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        // 1. 초기 로드: cursorId가 null 일 때
        if (cursorId == null) {
            return getInitialMessages(wrm.getWorkoutRoom(), wrm, size);
        }

        // 2. 이전 기록 스크롤 로드
        Pageable pageable = PageRequest.of(0, size);
        Slice<ChatMessage> messageSlice = chatMessageRepository.findByWorkoutRoomAndIdLessThanOrderByIdDesc(wrm.getWorkoutRoom(), cursorId, pageable);
        List<ChatMessageResponse> messages = messageSlice.getContent().stream()
                .map(ChatMessageResponse::from)
                .sorted(Comparator.comparing(ChatMessageResponse::getId))
                .toList();

        Long nextCursor = messages.isEmpty() ? null : messages.getFirst().getId();

        return new ChatHistoryResponse(messages, nextCursor, messageSlice.hasNext());
    }

    public void updateLastReadMessage(Member member, Long roomId) {
        WorkoutRoomMember wrm = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member).stream()
                .filter(workoutRoomMember -> workoutRoomMember.getWorkoutRoom().getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        // 현재 채팅방의 가장 최신 메시지를 찾음
        ChatMessage latestMessage = chatMessageRepository.findFirstByWorkoutRoomOrderByIdDesc(wrm.getWorkoutRoom());
        if (latestMessage != null) {
            wrm.setLastReadMessage(latestMessage);
            workoutRoomMemberRepository.save(wrm);
        }
    }

    public void markAsRead(Long roomId, Long messageId, Member member) {
        WorkoutRoomMember wrm = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member).stream()
                .filter(workoutRoomMember -> workoutRoomMember.getWorkoutRoom().getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));
        ChatMessage chatMessage = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        chatMessage.addReadBy(wrm.getNickname());
        chatMessageRepository.save(chatMessage);

        // 읽음 상태 업데이트 정보를 브로드캐스팅
        ChatMessageResponse response = ChatMessageResponse.from(chatMessage);
        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId + "/read", response);
    }

    private ChatHistoryResponse getInitialMessages(WorkoutRoom workoutRoom, WorkoutRoomMember workoutRoomMember, int size) {
        ChatMessage lastReadMessage = workoutRoomMember.getLastReadMessage();
        long lastReadId = (lastReadMessage != null) ? lastReadMessage.getId() : 0L;


        // 마지막으로 읽은 메시지 이후의 새로운 메시지들
        // TODO: 새로운 메시지들이 많으면 한번에 조회하면 안됨.. 추후 개선
        List<ChatMessage> newMessages = chatMessageRepository
                .findByWorkoutRoomAndIdGreaterThanOrderByIdAsc(workoutRoom, lastReadId);

        // 마지막으로 읽은 메시지를 포함한 이전 메시지들 (Pagination)
        Pageable pageable = PageRequest.of(0, size);
        Slice<ChatMessage> oldMessageSlice = chatMessageRepository
                .findByWorkoutRoomAndIdLessThanOrderByIdDesc(workoutRoom, lastReadId + 1, pageable);

        List<ChatMessage> oldMessages = oldMessageSlice.getContent().stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .toList();

        // 두 리스트 합쳐서 반환
        List<ChatMessageResponse> combinedMessages = Stream.concat(oldMessages.stream(), newMessages.stream())
                .map(ChatMessageResponse::from)
                .toList();

        Long nextCursor = oldMessages.isEmpty() ? null : oldMessages.getFirst().getId();

        return new ChatHistoryResponse(combinedMessages, nextCursor, oldMessageSlice.hasNext());
    }
}