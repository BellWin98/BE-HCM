package com.behcm.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jasypt-spring-boot 은 Spring Boot 4 를 아직 공식 지원하지 않는다(최신 4.0.4 도 Boot 3.5 기준 빌드).
 * 운영 설정(application-secret.yml)의 비밀값이 전부 ENC(...) 로 저장되어 있어, 이 연동이 깨지면
 * 애플리케이션이 부팅되지 않는다. 다른 테스트들은 평문 테스트 값을 쓰기 때문에 복호화 경로를
 * 전혀 지나가지 않으므로, 여기서 ENC(...) 복호화가 실제로 동작하는지 확인한다.
 *
 * 아래 암호문은 src/test/resources/application.yml 의 jasypt 설정(PBEWithMD5AndDES, base64,
 * 1000 iterations, 비밀번호 test-only-placeholder-password)과 JasyptConfig 의 인코더 구성으로
 * "jasypt-round-trip-ok" 를 암호화한 값이다.
 */
@SpringBootTest(properties = "test.jasypt.sample=ENC(hlThdhQRxbP1/nJO7UP71/EFL5MaqEYSRVc1b97vjac=)")
class JasyptEncryptablePropertyTest {

    @Value("${test.jasypt.sample}")
    private String decryptedValue;

    @Test
    @DisplayName("ENC(...) 로 저장된 프로퍼티가 주입 시점에 복호화된다")
    void decryptsEncryptedProperty() {
        assertThat(decryptedValue).isEqualTo("jasypt-round-trip-ok");
    }
}
