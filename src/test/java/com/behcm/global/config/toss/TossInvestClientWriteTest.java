package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * 쓰기 경로({@link TossInvestClient#post})의 재시도 안전성.
 *
 * <p>조회는 재시도가 무해하지만 주문은 아니다 — 한 번 더 보낸 요청이 한 건의 주문을 더 만든다.
 * "언제 다시 보내도 되는가"가 이 클래스에서 가장 중요한 성질이므로 실제 HTTP 계층
 * ({@link MockRestServiceServer})까지 내려가 요청 횟수로 검증한다.
 */
class TossInvestClientWriteTest {

    private static final String BASE_URL = "https://openapi.example";
    private static final String ORDERS_URL = BASE_URL + "/api/v1/orders";
    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final Long ACCOUNT_SEQ = 7L;

    private MockRestServiceServer server;
    private TossInvestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        TossInvestProperties properties = new TossInvestProperties();
        properties.getApi().setBaseUrl(BASE_URL);

        // 토큰 발급 자체는 이 테스트의 관심사가 아니다. 스토어가 항상 같은 토큰을 주도록 고정한다.
        TossTokenStore tokenStore = new StubTokenStore();
        client = new TossInvestClient(properties, builder.build(), tokenStore);
    }

    /** 토큰 발급/락을 타지 않고 고정 토큰만 돌려주는 스텁. */
    private static final class StubTokenStore extends TossTokenStore {
        private StubTokenStore() {
            super(null);
        }

        @Override
        public String getAccessToken(TossAccountOwner owner,
                                     java.util.function.Function<TossAccountOwner, IssuedToken> issuer) {
            return "test-token";
        }

        @Override
        public void evict(TossAccountOwner owner) {
        }
    }

    /**
     * package-private 인 {@code post} 를 직접 부른다 — 이 테스트가 같은 패키지에 있는 것 자체가
     * "쓰기 경로는 이 패키지 밖에서 보이지 않는다"는 설계를 확인해 준다.
     */
    private JsonNode post(boolean idempotent) {
        return client.post(OWNER, "/api/v1/orders", java.util.Map.of("symbol", "005930"),
                ACCOUNT_SEQ, idempotent);
    }

    @Test
    @DisplayName("주문 POST 는 계좌 헤더와 JSON 본문을 실어 보낸다")
    void sendsAccountHeaderAndJsonBody() {
        server.expect(once(), requestTo(ORDERS_URL))
                .andExpect(method(POST))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andRespond(withSuccess("{\"result\":{\"orderId\":\"o-1\"}}", APPLICATION_JSON));

        JsonNode result = post(true);

        assertThat(result.path("orderId").asString("")).isEqualTo("o-1");
        server.verify();
    }

    @Test
    @DisplayName("멱등키가 없는 주문은 429 를 받아도 재시도하지 않는다")
    void doesNotRetryNonIdempotentPostOnRateLimit() {
        // 재시도했다가 한도 응답과 실제 접수가 엇갈리면 주문이 두 번 들어간다.
        // 한 번만 보내고 바로 실패로 알리는 편이 훨씬 안전하다.
        server.expect(once(), requestTo(ORDERS_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"code\":\"rate-limited\"}}")
                        .contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> post(false))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOSS_RATE_LIMITED);

        server.verify();
    }

    @Test
    @DisplayName("멱등키가 있는 주문은 429 를 받으면 재시도한다")
    void retriesIdempotentPostOnRateLimit() {
        server.expect(once(), requestTo(ORDERS_URL))
                .andRespond(withStatus(TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"code\":\"rate-limited\"}}")
                        .contentType(APPLICATION_JSON));
        server.expect(once(), requestTo(ORDERS_URL))
                .andRespond(withSuccess("{\"result\":{\"orderId\":\"o-2\"}}", APPLICATION_JSON));

        assertThat(post(true).path("orderId").asString("")).isEqualTo("o-2");

        server.verify();
    }

    @Test
    @DisplayName("401 은 토큰을 재발급해 한 번만 재시도한다")
    void retriesOnceAfterTokenRefresh() {
        // 401 은 요청이 원장에 닿기 전에 거부된 것이라 쓰기 경로에서도 재시도가 안전하다.
        server.expect(once(), requestTo(ORDERS_URL))
                .andRespond(withStatus(UNAUTHORIZED).body("{\"error\":{\"code\":\"unauthorized\"}}")
                        .contentType(APPLICATION_JSON));
        server.expect(once(), requestTo(ORDERS_URL))
                .andRespond(withSuccess("{\"result\":{\"orderId\":\"o-3\"}}", APPLICATION_JSON));

        assertThat(post(false).path("orderId").asString("")).isEqualTo("o-3");

        server.verify();
    }

    @Test
    @DisplayName("주문 실패는 error.code 를 보고 세분화된 에러로 바뀐다")
    void mapsOrderErrorsByCode() {
        server.expect(once(), requestTo(ORDERS_URL))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .body("{\"error\":{\"code\":\"insufficient-buying-power\",\"message\":\"토스 원문\"}}")
                        .contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> post(true))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOSS_ORDER_INSUFFICIENT_BUYING_POWER);
    }

    @Test
    @DisplayName("주문 에러 메시지에 토스 원문을 그대로 내보내지 않는다")
    void doesNotLeakTossMessage() {
        server.expect(once(), requestTo(ORDERS_URL))
                .andRespond(withStatus(UNPROCESSABLE_ENTITY)
                        .body("{\"error\":{\"code\":\"stock-restricted\",\"message\":\"내부용 문구\"}}")
                        .contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> post(true))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.TOSS_ORDER_STOCK_RESTRICTED.getMessage())
                .hasMessageNotContaining("내부용 문구");
    }
}
