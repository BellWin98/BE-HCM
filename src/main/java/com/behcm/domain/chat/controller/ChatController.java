package com.behcm.domain.chat.controller;

import com.behcm.domain.chat.dto.ChatMessageRequest;
import com.behcm.domain.chat.dto.ChatMessageResponse;
import com.behcm.domain.chat.service.ChatService;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 클라이언트가 /app/chat/room/{roomId}/send로 메시지를 보내면 이 메서드가 처리
    @MessageMapping("/chat/room/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            StompHeaderAccessor headerAccessor,
            @Payload ChatMessageRequest request
    ) {
        Authentication authentication = (Authentication) headerAccessor.getUser();
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

    // 채팅방의 이전 대화 기록을 가져오는 API
    @GetMapping("/api/chat/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getChatHistory(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Member member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<ChatMessageResponse> response = chatService.getChatHistory(member, roomId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
