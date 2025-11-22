package com.behcm.domain.penalty.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RestTemplate restTemplate;

    @Value("${payment.pg.api-url:https://api.example-pg.com}")
    private String pgApiUrl;

    @Value("${payment.pg.api-key:your-pg-api-key}")
    private String pgApiKey;

    @Value("${payment.pg.merchant-id:your-merchant-id}")
    private String merchantId;

    public PaymentResult processPayment(Long amount, String orderId) {
        try {
            Map<String, Object> requestBody = createPaymentRequest(amount, orderId);
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String url = pgApiUrl + "/payments";
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            return processPaymentResponse(response.getBody());

        } catch (Exception e) {
            log.error("Payment processing failed for orderId: {}, amount: {}", orderId, amount, e);
            return PaymentResult.builder()
                    .success(false)
                    .message("결제 처리 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
        }
    }

    private Map<String, Object> createPaymentRequest(Long amount, String orderId) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("merchant_id", merchantId);
        requestBody.put("order_id", orderId);
        requestBody.put("amount", amount);
        requestBody.put("currency", "KRW");
        requestBody.put("description", "벌금 납부");
        requestBody.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return requestBody;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + pgApiKey);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private PaymentResult processPaymentResponse(Map<String, Object> responseBody) {
        if (responseBody == null) {
            return PaymentResult.builder()
                    .success(false)
                    .message("PG사 응답이 없습니다")
                    .build();
        }

        String status = (String) responseBody.get("status");
        if ("SUCCESS".equals(status) || "COMPLETED".equals(status)) {
            return PaymentResult.builder()
                    .success(true)
                    .transactionId((String) responseBody.get("transaction_id"))
                    .message("결제가 성공적으로 완료되었습니다")
                    .build();
        } else {
            return PaymentResult.builder()
                    .success(false)
                    .message((String) responseBody.getOrDefault("message", "결제 실패"))
                    .build();
        }
    }

    public static class PaymentResult {
        private final boolean success;
        private final String transactionId;
        private final String message;

        private PaymentResult(boolean success, String transactionId, String message) {
            this.success = success;
            this.transactionId = transactionId;
            this.message = message;
        }

        public static PaymentResultBuilder builder() {
            return new PaymentResultBuilder();
        }

        public boolean isSuccess() { return success; }
        public String getTransactionId() { return transactionId; }
        public String getMessage() { return message; }

        public static class PaymentResultBuilder {
            private boolean success;
            private String transactionId;
            private String message;

            public PaymentResultBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public PaymentResultBuilder transactionId(String transactionId) {
                this.transactionId = transactionId;
                return this;
            }

            public PaymentResultBuilder message(String message) {
                this.message = message;
                return this;
            }

            public PaymentResult build() {
                return new PaymentResult(success, transactionId, message);
            }
        }
    }
}