package com.behcm.global.config.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KoreaInvestmentClient {

    private final KoreaInvestmentProperties properties;
//    private final RestTemplate restTemplate = new RestTemplate();
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String accessToken;

    public String getAccessToken() {
        if (accessToken != null) {
            return accessToken;
        }

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
            this.accessToken = responseJson.get("access_token").asText();
            return this.accessToken;
        } catch (Exception e) {
            log.error("Failed to get access token", e);
            throw new RuntimeException("Failed to get access token", e);
        }
    }

    public JsonNode callApi(String endpoint, String transactionId) {
        String url = properties.getBaseUrl() + endpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + getAccessToken());
        headers.set("appkey", properties.getAppKey());
        headers.set("appsecret", properties.getAppSecret());
        headers.set("tr_id", transactionId);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Failed to call Korea Investment API: {}", url, e);
            throw new RuntimeException("Failed to call Korea Investment API", e);
        }
    }

    public JsonNode callApiWithParams(String endpoint, String transactionId, Map<String, String> params) {
        StringBuilder urlBuilder = new StringBuilder(properties.getBaseUrl() + endpoint);

        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            params.forEach((key, value) -> urlBuilder.append(key).append("=").append(value).append("&"));
            urlBuilder.setLength(urlBuilder.length() - 1);
        }

        String url = urlBuilder.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + getAccessToken());
        headers.set("appkey", properties.getAppKey());
        headers.set("appsecret", properties.getAppSecret());
        headers.set("tr_id", transactionId);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Failed to call Korea Investment API: {}", url, e);
            throw new RuntimeException("Failed to call Korea Investment API", e);
        }
    }
}