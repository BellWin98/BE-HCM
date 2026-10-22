package com.behcm.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각의 출처. 시간에 따라 동작이 갈리는 코드(예: 주문내역 신선도 창)가 {@code now()} 를 직접 부르지 않고
 * 이 빈을 주입받으면 테스트가 시간을 움직일 수 있다.
 *
 * <p>시스템 기본 시간대를 쓴다 — 운영 컨테이너는 {@code -Duser.timezone=Asia/Seoul} 로 뜨므로(Dockerfile)
 * {@code LocalDateTime.now()} 를 쓰던 기존 동작과 같다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
