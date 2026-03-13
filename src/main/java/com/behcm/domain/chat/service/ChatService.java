package com.behcm.domain.chat.service;

import com.behcm.domain.chat.dto.ChatHistoryResponse;
import com.behcm.domain.chat.dto.ChatImageUploadResponse;
import com.behcm.domain.chat.dto.ChatMessageRequest;
import com.behcm.domain.chat.dto.ChatMessageResponse;
import com.behcm.domain.chat.dto.ReadStatusMessage;
import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.chat.entity.MessageType;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRoomRepository workoutRoomRepository;
    private final S3Service s3Service;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatImageUploadResponse uploadChatImage(Member member, Long roomId, MultipartFile file) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));
        if (!workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(member, workoutRoom)) {
            throw new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER);
        }
        String imageUrl = s3Service.uploadChatImage(file, workoutRoom.getId());
        return ChatImageUploadResponse.of(imageUrl);
    }

    public void sendMessage(Long roomId, Member sender, ChatMessageRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));
        if (!workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(sender, workoutRoom)) {
            throw new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER);
        }
        if (request.getType() == MessageType.IMAGE) {
            if (!StringUtils.hasText(request.getImageUrl())) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "이미지 URL이 필요합니다.");
            }
        }

        String imageUrl = (request.getType() == MessageType.IMAGE)
                ? request.getImageUrl().trim()
                : null;
        ChatMessage chatMessage = ChatMessage.builder()
                .sender(sender)
                .workoutRoom(workoutRoom)
                .content(request.getContent() != null ? request.getContent() : "")
                .messageType(request.getType())
                .imageUrl(imageUrl)
                .build();

        ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);

        // 보낸 사람은 해당 메시지를 즉시 읽은 것으로 간주하여 lastReadMessage 갱신
        WorkoutRoomMember senderWorkoutRoomMember = workoutRoomMemberRepository
                .findByWorkoutRoomAndMember(workoutRoom, sender)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));
        ChatMessage senderCurrentLastRead = senderWorkoutRoomMember.getLastReadMessage();
        if (senderCurrentLastRead == null || senderCurrentLastRead.getId() < savedChatMessage.getId()) {
            senderWorkoutRoomMember.setLastReadMessage(savedChatMessage);
            workoutRoomMemberRepository.save(senderWorkoutRoomMember);
        }

        List<ChatMessageResponse> responses = toResponsesWithUnread(workoutRoom, List.of(savedChatMessage));
        ChatMessageResponse response = responses.isEmpty() ? ChatMessageResponse.from(savedChatMessage) : responses.getFirst();

        messagingTemplate.convertAndSend("/topic/chat/room/" + roomId, response);
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getChatHistory(Member member, Long roomId, Long cursorId, int size) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));
        WorkoutRoomMember wrm = workoutRoomMemberRepository.findByWorkoutRoomAndMember(workoutRoom, member)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        // 1. 초기 로드: cursorId가 null 일 때
        if (cursorId == null) {
            return getInitialMessages(wrm.getWorkoutRoom(), wrm, size);
        }

        // 2. 이전 기록 스크롤 로드
        Pageable pageable = PageRequest.of(0, size);
        Slice<ChatMessage> messageSlice = chatMessageRepository
                .findByWorkoutRoomAndIdLessThanOrderByIdDesc(wrm.getWorkoutRoom(), cursorId, pageable);

        List<ChatMessage> chatMessages = messageSlice.getContent().stream()
                .sorted(Comparator.comparing(ChatMessage::getId))
                .toList();

        List<ChatMessageResponse> messages = toResponsesWithUnread(wrm.getWorkoutRoom(), chatMessages);
        Long nextCursor = chatMessages.isEmpty() ? null : chatMessages.getFirst().getId();

        return new ChatHistoryResponse(messages, nextCursor, messageSlice.hasNext());
    }

    public void updateLastReadMessage(Member member, Long roomId) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));
        WorkoutRoomMember wrm = workoutRoomMemberRepository.findByWorkoutRoomAndMember(workoutRoom, member)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        // 현재 채팅방의 가장 최신 메시지를 찾음
        ChatMessage latestMessage = chatMessageRepository.findFirstByWorkoutRoomOrderByIdDesc(wrm.getWorkoutRoom());
        if (latestMessage == null) {
            return;
        }

        ChatMessage currentLastRead = wrm.getLastReadMessage();
        if (currentLastRead == null || currentLastRead.getId() < latestMessage.getId()) {
            wrm.setLastReadMessage(latestMessage);
            workoutRoomMemberRepository.save(wrm);
        }

        // 최신 읽음 상태를 기반으로 최근 메시지들의 unreadCount를 재계산하여 브로드캐스트
        Pageable pageable = PageRequest.of(0, 200);
        List<ChatMessage> recentMessages =
                chatMessageRepository.findByWorkoutRoomOrderByIdDesc(wrm.getWorkoutRoom(), pageable);

        List<ChatMessageResponse> responses = toResponsesWithUnread(wrm.getWorkoutRoom(), recentMessages);
        List<ReadStatusMessage.UpdatedMessage> updatedMessages = responses.stream()
                .map(r -> new ReadStatusMessage.UpdatedMessage(r.getId(), r.getUnreadCount()))
                .collect(Collectors.toList());

        if (!updatedMessages.isEmpty()) {
            ReadStatusMessage readStatusMessage = ReadStatusMessage.of(updatedMessages);
            messagingTemplate.convertAndSend("/topic/chat/room/" + roomId, readStatusMessage);
        }
    }

    public void markAsRead(Long roomId, Long messageId, Member member) {
        // STOMP를 통한 읽음 처리 요청은 REST API 기반 읽음 처리와 동일하게 동작하도록 통합
        updateLastReadMessage(member, roomId);
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

        // 두 리스트 합쳐서 unreadCount 포함 응답 생성
        List<ChatMessage> combinedMessages = Stream.concat(oldMessages.stream(), newMessages.stream())
                .sorted(Comparator.comparing(ChatMessage::getId))
                .toList();

        List<ChatMessageResponse> combinedResponses = toResponsesWithUnread(workoutRoom, combinedMessages);
        Long nextCursor = oldMessages.isEmpty() ? null : oldMessages.getFirst().getId();

        return new ChatHistoryResponse(combinedResponses, nextCursor, oldMessageSlice.hasNext());
    }

    private List<ChatMessageResponse> toResponsesWithUnread(WorkoutRoom workoutRoom, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        List<WorkoutRoomMember> members =
                workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(workoutRoom);

        List<Long> lastReadIds = members.stream()
                .map(WorkoutRoomMember::getLastReadMessage)
                .map(lastRead -> lastRead != null ? lastRead.getId() : null)
                .toList();

        return messages.stream()
                .map(message -> {
                    int unread = 0;
                    Long messageId = message.getId();
                    for (Long lastReadId : lastReadIds) {
                        if (lastReadId == null || lastReadId < messageId) {
                            unread++;
                        }
                    }
                    return ChatMessageResponse.from(message, unread);
                })
                .toList();
    }
}