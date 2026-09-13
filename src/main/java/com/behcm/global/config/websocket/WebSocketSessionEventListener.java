package com.behcm.global.config.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * STOMP 세션의 수명주기를 남긴다. "채팅이 안 와요"의 첫 질문은 "그 시각에 연결이 살아 있었나"다 —
 * 연결/해제 기록이 없으면 그 질문에 답할 수 없다.
 */
@Slf4j
@Component
public class WebSocketSessionEventListener {

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("STOMP connected (session={}, user={})", accessor.getSessionId(), userName(event));
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("STOMP subscribed (session={}, user={}, destination={})",
                accessor.getSessionId(), userName(event), accessor.getDestination());
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        // closeStatus 1000 은 정상 종료, 1006 은 네트워크 단절(모바일 백그라운드 전환 등)이다.
        log.info("STOMP disconnected (session={}, user={}, closeStatus={})",
                event.getSessionId(), userName(event), event.getCloseStatus());
    }

    private static String userName(org.springframework.web.socket.messaging.AbstractSubProtocolEvent event) {
        return event.getUser() != null ? event.getUser().getName() : null;
    }
}
