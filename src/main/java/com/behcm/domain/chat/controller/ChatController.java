package com.behcm.domain.chat.controller;

import com.behcm.domain.chat.dto.ChatHistoryResponse;
import com.behcm.domain.chat.dto.ChatImageUploadResponse;
import com.behcm.domain.chat.dto.ChatMessageRequest;
import com.behcm.domain.chat.service.ChatService;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.global.exception.ErrorLogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /** STOMP 오류를 보낸 사람에게만 돌려주는 목적지. 클라이언트는 {@code /user/queue/errors} 로 구독한다. */
    private static final String ERROR_QUEUE = "/queue/errors";

    // 클라이언트가 /app/chat/room/{roomId}/send로 메시지를 보내면 이 메서드가 처리
    @MessageMapping("/chat/room/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            StompHeaderAccessor headerAccessor,
            @Payload ChatMessageRequest request
    ) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication == null) {
            // JwtChannelInterceptor 가 토큰 없는 프레임을 통과시킨 경우. NPE 로 터뜨리면 원인이 묻힌다.
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        Member member = (Member) authentication.getPrincipal();

        chatService.sendMessage(roomId, member, request);
    }

    // 클라이언트가 /app/room/{roomId}/chat/read 로 메시지 읽음 확인을 보내면 이 메서드가 처리합니다.
    @MessageMapping("/chat/room/{roomId}/read/{messageId}")
    public void markAsRead(
            @DestinationVariable Long roomId,
            @DestinationVariable Long messageId,
            @AuthenticationPrincipal Member member
    ) {
        chatService.markAsRead(roomId, messageId, member);
    }

    // 채팅 이미지 업로드
    @PostMapping("/api/chat/rooms/{roomId}/images")
    public ResponseEntity<ApiResponse<ChatImageUploadResponse>> uploadChatImage(
            @PathVariable Long roomId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Member member
    ) {
        ChatImageUploadResponse response = chatService.uploadChatImage(member, roomId, file);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 채팅방의 이전 대화 기록을 가져오는 API
    @GetMapping("/api/chat/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getChatHistory(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Member member,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        ChatHistoryResponse response = chatService.getChatHistory(member, roomId, cursorId, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * {@code @MessageMapping} 에서 던진 예외는 {@code @RestControllerAdvice} 를 타지 않는다.
     * 이 핸들러가 없으면 "Unhandled exception from message handler method" ERROR 만 남고 클라이언트는
     * 아무 응답도 받지 못한다. 레벨은 HTTP 와 같은 기준({@link ErrorLogLevel})으로 정한다.
     */
    @MessageExceptionHandler(CustomException.class)
    public void handleCustomException(CustomException e, StompHeaderAccessor headerAccessor) {
        ErrorCode errorCode = e.getErrorCode();
        Level level = ErrorLogLevel.of(errorCode);
        var event = log.atLevel(level);
        if (level == Level.ERROR) {
            event = event.setCause(e);
        }
        event.log("[{}] STOMP {} (session={}, user={}) - {}",
                errorCode, headerAccessor.getDestination(), headerAccessor.getSessionId(),
                userName(headerAccessor), e.getMessage());
        replyError(headerAccessor, e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception e, StompHeaderAccessor headerAccessor) {
        log.error("[UNHANDLED] STOMP {} (session={}, user={}) - {}: {}",
                headerAccessor.getDestination(), headerAccessor.getSessionId(),
                userName(headerAccessor), e.getClass().getName(), e.getMessage(), e);
        replyError(headerAccessor, ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    private void replyError(StompHeaderAccessor headerAccessor, String message) {
        String user = userName(headerAccessor);
        if (user == null) {
            return;
        }
        messagingTemplate.convertAndSendToUser(user, ERROR_QUEUE, ApiResponse.error(message));
    }

    private static String userName(StompHeaderAccessor headerAccessor) {
        return headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
    }

    // 유저가 채팅을 읽었음을 서버에 알리는 API
    @PostMapping("/api/chat/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> updateLastReadMessage(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Member member
    ) {
        chatService.updateLastReadMessage(member, roomId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
