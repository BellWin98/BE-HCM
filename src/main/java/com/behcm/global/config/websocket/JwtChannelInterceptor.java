package com.behcm.global.config.websocket;

import com.behcm.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // CONNECT, SEND, SUBSCRIBE 명령어 처리시 jwt 토큰 검증
        if (StompCommand.CONNECT.equals(accessor.getCommand()) ||
            StompCommand.SEND.equals(accessor.getCommand()) ||
            StompCommand.SUBSCRIBE.equals(accessor.getCommand())
        ) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && jwtTokenProvider.validateToken(token.replace("Bearer ", ""))) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token.replace("Bearer ", ""));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                accessor.setUser(authentication); // WebSocket 세션에 사용자 정보 저장
            }
        }

        return message;
    }
}
