package com.behcm.global.config.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KoreaInvestmentClient {

    private final KoreaInvestmentProperties properties;
    private final RestClient restClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JsonMapper objectMapper = new JsonMapper();

    private static final String ACCESS_TOKEN_KEY = "korea_investment:access_token";
    private static final String ACCESS_TOKEN_EXPIRY_KEY = "korea_investment:access_token_expiry";

    public String getAccessToken() {
        String cachedToken = (String) redisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        String cachedExpiry = (String) redisTemplate.opsForValue().get(ACCESS_TOKEN_EXPIRY_KEY);

        if (cachedToken != null && cachedExpiry != null) {
            LocalDateTime expiryTime = LocalDateTime.parse(cachedExpiry, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (LocalDateTime.now().isBefore(expiryTime)) {
                log.debug("Using cached access token");
                return cachedToken;
            }
        }

        log.info("Fetching new access token from Korea Investment API");
        return fetchNewAccessToken();
    }

    private String fetchNewAccessToken() {
        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", properties.getAppKey());
        body.put("appsecret", properties.getAppSecret());

        try {
            String response = restClient.post()
                    .uri(properties.getBaseUrl() + "/oauth2/tokenP")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode responseJson = objectMapper.readTree(response);

            String accessToken = responseJson.get("access_token").asString();
            String expiryTimeStr = responseJson.get("access_token_token_expired").asString();
            int expiresInSeconds = responseJson.get("expires_in").asInt();

            Duration tokenTtl = Duration.ofSeconds(expiresInSeconds - 300L);
            redisTemplate.opsForValue().set(ACCESS_TOKEN_KEY, accessToken, tokenTtl);
            redisTemplate.opsForValue().set(ACCESS_TOKEN_EXPIRY_KEY, expiryTimeStr, tokenTtl);

            log.info("New access token cached successfully. Expires at: {}", expiryTimeStr);
            return accessToken;
        } catch (Exception e) {
            log.error("Failed to get access token", e);
            clearTokenCache();
            throw new RuntimeException("Failed to get access token", e);
        }
    }

    public JsonNode callApi(String endpoint, String transactionId) {
        return callApiWithRetry(endpoint, transactionId, null, new HashMap<>(), false);
    }

    public JsonNode callApiWithParams(String endpoint, String transactionId, Map<String, String> params) {
        return callApiWithRetry(endpoint, transactionId, params, new HashMap<>(), false);
    }

    public JsonNode callApiWithParams(String endpoint, String transactionId, Map<String, String> params, Map<String, String> customHeaders) {
        return callApiWithRetry(endpoint, transactionId, params, customHeaders, false);
    }

    private JsonNode callApiWithRetry(
            String endpoint, String transactionId, Map<String, String> params,
            Map<String, String> customHeaders, boolean isRetry
    ) {
        String accessToken = getAccessToken();

        try {
            String response = restClient.get()
                    .uri(properties.getBaseUrl(), uriBuilder -> {
                        uriBuilder.path(endpoint);

                        // 값을 queryParam 에 리터럴로 넘기면 URI 템플릿의 일부로 취급되어
                        // 쿼리 컴포넌트에서 합법인 문자(&, = 등)가 인코딩되지 않는다.
                        // 값을 URI 변수로 넘겨야 엄격하게 인코딩된다.
                        Map<String, Object> queryValues = new HashMap<>();
                        if (params != null) {
                            params.forEach((key, value) -> {
                                uriBuilder.queryParam(key, "{" + key + "}");
                                queryValues.put(key, value);
                            });
                        }
                        return uriBuilder.build(queryValues);
                    })
                    .headers(headers -> {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("authorization", "Bearer " + accessToken);
                        headers.set("appkey", properties.getAppKey());
                        headers.set("appsecret", properties.getAppSecret());
                        headers.set("tr_id", transactionId);
                        headers.set("custtype", "P");

                        if (customHeaders != null && !customHeaders.isEmpty()) {
                            customHeaders.forEach(headers::set);
                        }
                    })
                    .retrieve()
                    .body(String.class);

            JsonNode responseJson = objectMapper.readTree(response);

            if (isTokenExpiredResponse(responseJson)) {
                return handleTokenExpiry(endpoint, transactionId, params, customHeaders, isRetry);
            }

            return responseJson;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED && !isRetry) {
                log.warn("Received 401 Unauthorized, attempting token refresh");
                return handleTokenExpiry(endpoint, transactionId, params, customHeaders, isRetry);
            }
            log.error("Failed to call Korea Investment API: {}", endpoint, e);
            throw new RuntimeException("Failed to call Korea Investment API", e);
        } catch (Exception e) {
            log.error("Failed to call Korea Investment API: {}", endpoint, e);
            throw new RuntimeException("Failed to call Korea Investment API", e);
        }
    }

    private boolean isTokenExpiredResponse(JsonNode responseJson) {
        if (responseJson.has("rt_cd")) {
            String rtCd = responseJson.get("rt_cd").asString();
            return "EGW00123".equals(rtCd) || "EGW00124".equals(rtCd);
        }
        return false;
    }

    private JsonNode handleTokenExpiry(String endpoint, String transactionId, Map<String, String> params, Map<String, String> customHeaders, boolean isRetry) {
        if (isRetry) {
            log.error("Token refresh failed, cannot retry again");
            throw new RuntimeException("Token refresh failed after retry");
        }

        log.info("Token expired, clearing cache and fetching new token");
        clearTokenCache();

        return callApiWithRetry(endpoint, transactionId, params, customHeaders, true);
    }

    private void clearTokenCache() {
        redisTemplate.delete(ACCESS_TOKEN_KEY);
        redisTemplate.delete(ACCESS_TOKEN_EXPIRY_KEY);
        log.info("Token cache cleared");
    }

    public void forceTokenRefresh() {
        log.info("Forcing token refresh");
        clearTokenCache();
        getAccessToken();
    }
}