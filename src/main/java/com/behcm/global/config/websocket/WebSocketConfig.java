package com.behcm.global.config.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트에서 WebSocket에 연결할 때 사용할 엔드포인트를 지정합니다.
        // '/ws' 경로로 지정하고, CORS 문제를 해결하기 위해 setAllowedOriginPatterns("*")를 추가합니다.
        registry
                .addEndpoint("/wss")
                .setAllowedOriginPatterns("http://localhost:*", "https://localhost:*", "https://hcm-red.vercel.app", "https://hcm-blue.vercel.app")
                .withSockJS(); // SockJS는 WebSocket을 지원하지 않는 브라우저에서도 유사한 경험을 제공합니다.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메시지 브로커가 /topic으로 시작하는 경로를 구독하는 클라이언트에게 메시지를 전달하도록 설정합니다.
        registry.enableSimpleBroker("/topic");
        // 클라이언트에서 서버로 메시지를 보낼 때 사용하는 경로의 접두사를 /app으로 설정합니다.
        // 예를 들어, /app/chat/send 와 같은 경로로 메시지를 보내면 컨트롤러의 @MessageMapping이 이를 처리합니다.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {

        // 클라이언트로부터 들어오는 메시지를 처리하는 채널에 인터셉터를 등록
        registration.interceptors(jwtChannelInterceptor);
    }
}
