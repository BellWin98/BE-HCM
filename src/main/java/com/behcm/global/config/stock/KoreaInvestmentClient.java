package com.behcm.global.config.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KoreaInvestmentClient {

    private final KoreaInvestmentProperties properties;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        String url = properties.getBaseUrl() + "/oauth2/tokenP";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", properties.getAppKey());
        body.put("appsecret", properties.getAppSecret());

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            String accessToken = responseJson.get("access_token").asText();
            String expiryTimeStr = responseJson.get("access_token_token_expired").asText();
            int expiresInSeconds = responseJson.get("expires_in").asInt();

            redisTemplate.opsForValue().set(ACCESS_TOKEN_KEY, accessToken, expiresInSeconds - 300, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(ACCESS_TOKEN_EXPIRY_KEY, expiryTimeStr, expiresInSeconds - 300, TimeUnit.SECONDS);

            log.info("New access token cached successfully. Expires at: {}", expiryTimeStr);
            return accessToken;
        } catch (Exception e) {
            log.error("Failed to get access token", e);
            clearTokenCache();
            throw new RuntimeException("Failed to get access token", e);
        }
    }

    public JsonNode callApi(String endpoint, String transactionId) {
        return callApiWithRetry(endpoint, transactionId, null, false);
    }

    public JsonNode callApiWithParams(String endpoint, String transactionId, Map<String, String> params) {
        return callApiWithRetry(endpoint, transactionId, params, false);
    }

    private JsonNode callApiWithRetry(String endpoint, String transactionId, Map<String, String> params, boolean isRetry) {
        String url = buildUrl(endpoint, params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + getAccessToken());
        headers.set("appkey", properties.getAppKey());
        headers.set("appsecret", properties.getAppSecret());
        headers.set("tr_id", transactionId);
        headers.set("custtype", "P");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            if (isTokenExpiredResponse(responseJson)) {
                return handleTokenExpiry(endpoint, transactionId, params, isRetry);
            }

            return responseJson;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED && !isRetry) {
                log.warn("Received 401 Unauthorized, attempting token refresh");
                return handleTokenExpiry(endpoint, transactionId, params, isRetry);
            }
            log.error("Failed to call Korea Investment API: {}", url, e);
            throw new RuntimeException("Failed to call Korea Investment API", e);
        } catch (Exception e) {
            log.error("Failed to call Korea Investment API: {}", url, e);
            throw new RuntimeException("Failed to call Korea Investment API", e);
        }
    }

    private String buildUrl(String endpoint, Map<String, String> params) {
        StringBuilder urlBuilder = new StringBuilder(properties.getBaseUrl() + endpoint);

        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            params.forEach((key, value) -> urlBuilder.append(key).append("=").append(value).append("&"));
            urlBuilder.setLength(urlBuilder.length() - 1);
        }

        return urlBuilder.toString();
    }

    private boolean isTokenExpiredResponse(JsonNode responseJson) {
        if (responseJson.has("rt_cd")) {
            String rtCd = responseJson.get("rt_cd").asText();
            return "EGW00123".equals(rtCd) || "EGW00124".equals(rtCd);
        }
        return false;
    }

    private JsonNode handleTokenExpiry(String endpoint, String transactionId, Map<String, String> params, boolean isRetry) {
        if (isRetry) {
            log.error("Token refresh failed, cannot retry again");
            throw new RuntimeException("Token refresh failed after retry");
        }

        log.info("Token expired, clearing cache and fetching new token");
        clearTokenCache();

        return callApiWithRetry(endpoint, transactionId, params, true);
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