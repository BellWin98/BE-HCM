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
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command) ||
            StompCommand.SEND.equals(command) ||
            StompCommand.SUBSCRIBE.equals(command)
        ) {
            String header = accessor.getFirstNativeHeader("Authorization");
            String token = header != null ? header.replace("Bearer ", "") : null;
            if (token != null && jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                accessor.setUser(authentication); // WebSocket 세션에 사용자 정보 저장
                if (StompCommand.CONNECT.equals(command)) {
                    log.debug("STOMP CONNECT authenticated (session={}, user={})",
                            accessor.getSessionId(), authentication.getName());
                }
            } else {
                // 인증 없이 통과시키면 ChatController 에서 principal 이 null 이라 NPE 로 터진다.
                // 그 NPE 의 원인이 여기라는 것을 알 수 있도록 세션 단위로 남긴다.
                log.warn("STOMP {} without a valid token (session={}, tokenPresent={})",
                        command, accessor.getSessionId(), token != null);
            }
        }

        return message;
    }
}
