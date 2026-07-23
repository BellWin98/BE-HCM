package com.behcm.global.config.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class KoreaInvestmentClientTest {

    private static final String BASE_URL = "https://openapi.test";
    private static final String ENDPOINT = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String TR_ID = "FHKST01010100";
    private static final String ACCESS_TOKEN_KEY = "korea_investment:access_token";
    private static final String ACCESS_TOKEN_EXPIRY_KEY = "korea_investment:access_token_expiry";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private MockRestServiceServer server;
    private KoreaInvestmentClient client;

    @BeforeEach
    void setUp() {
        KoreaInvestmentProperties properties = new KoreaInvestmentProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setAppKey("test-app-key");
        properties.setAppSecret("test-app-secret");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KoreaInvestmentClient(properties, builder.build(), redisTemplate);
    }

    /** 만료 전인 토큰이 Redis 에 캐시되어 있는 상태를 만든다. */
    private void givenCachedToken(String token) {
        String expiry = LocalDateTime.now().plusHours(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(ACCESS_TOKEN_KEY)).willReturn(token);
        given(valueOperations.get(ACCESS_TOKEN_EXPIRY_KEY)).willReturn(expiry);
    }

    @Test
    @DisplayName("쿼리 파라미터의 값은 URL 인코딩되고, 빈 값도 이름=형태로 유지된다")
    void callApiWithParams_encodesQueryParameterValues() {
        givenCachedToken("cached-token");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("FID_INPUT_ISCD", "005930");
        params.put("CTX_AREA_FK100", "");
        params.put("RAW", "a b&c=d");

        server.expect(once(), requestTo(
                        BASE_URL + ENDPOINT + "?FID_INPUT_ISCD=005930&CTX_AREA_FK100=&RAW=a%20b%26c%3Dd"))
                .andExpect(header("authorization", "Bearer cached-token"))
                .andExpect(header("tr_id", TR_ID))
                .andExpect(header("custtype", "P"))
                .andRespond(withSuccess("{\"rt_cd\":\"0\"}", MediaType.APPLICATION_JSON));

        JsonNode response = client.callApiWithParams(ENDPOINT, TR_ID, params);

        assertThat(response.get("rt_cd").asString()).isEqualTo("0");
        server.verify();
    }

    @Test
    @DisplayName("파라미터가 없으면 쿼리스트링 없이 호출한다")
    void callApi_withoutParams_hasNoQueryString() {
        givenCachedToken("cached-token");

        server.expect(once(), requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess("{\"rt_cd\":\"0\"}", MediaType.APPLICATION_JSON));

        client.callApi(ENDPOINT, TR_ID);

        server.verify();
    }

    @Test
    @DisplayName("401 응답을 받으면 토큰 캐시를 지우고 한 번 재시도한다")
    void callApi_on401_clearsTokenCacheAndRetriesOnce() {
        givenCachedToken("expired-token");

        server.expect(once(), requestTo(BASE_URL + ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess("{\"rt_cd\":\"0\"}", MediaType.APPLICATION_JSON));

        JsonNode response = client.callApi(ENDPOINT, TR_ID);

        assertThat(response.get("rt_cd").asString()).isEqualTo("0");
        server.verify();
        // 재시도 전에 캐시를 비웠는지 확인한다.
        org.mockito.Mockito.verify(redisTemplate).delete(ACCESS_TOKEN_KEY);
        org.mockito.Mockito.verify(redisTemplate).delete(ACCESS_TOKEN_EXPIRY_KEY);
    }

    @Test
    @DisplayName("응답 본문의 rt_cd 가 토큰 만료 코드면 캐시를 비우고 재시도한다")
    void callApi_onTokenExpiredResponseCode_retriesOnce() {
        givenCachedToken("expired-token");

        server.expect(once(), requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess("{\"rt_cd\":\"EGW00123\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + ENDPOINT))
                .andRespond(withSuccess("{\"rt_cd\":\"0\"}", MediaType.APPLICATION_JSON));

        JsonNode response = client.callApi(ENDPOINT, TR_ID);

        assertThat(response.get("rt_cd").asString()).isEqualTo("0");
        server.verify();
    }
}
