package com.behcm.domain.payment.service;

import com.behcm.domain.payment.dto.*;
import com.behcm.domain.payment.entity.Payment;
import com.behcm.domain.payment.entity.PaymentStatus;
import com.behcm.domain.payment.repository.PaymentRepository;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TossPaymentService {

    private final PaymentRepository paymentRepository;
    private final PenaltyRepository penaltyRepository;
    private final RestTemplate restTemplate;

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    private static final String TOSS_PAYMENTS_BASE_URL = "https://api.tosspayments.com/v1/payments";

    public CreatePaymentOrderResponse createPaymentOrder(CreatePaymentOrderRequest request) {
        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Penalty penalty = penaltyRepository.findById(request.getPenaltyRecordId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (penalty.getIsPaid()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .amount(BigDecimal.valueOf(request.getAmount()))
                .status(PaymentStatus.READY)
                .method("카드")
                .penalty(penalty)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        return CreatePaymentOrderResponse.from(savedPayment);
    }

    public ConfirmPaymentResponse confirmPayment(ConfirmPaymentRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!payment.getAmount().equals(BigDecimal.valueOf(request.getAmount()))) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        try {
            Map<String, Object> tossResponse = callTossPaymentsConfirm(request);

            payment.updatePaymentKey(request.getPaymentKey());
            payment.updateStatus(PaymentStatus.DONE);

            Penalty penalty = payment.getPenalty();
            penalty.markAsPaid();

            return ConfirmPaymentResponse.from(payment);

        } catch (Exception e) {
            log.error("Payment confirmation failed for orderId: {}", request.getOrderId(), e);
            payment.updateStatus(PaymentStatus.ABORTED);
            payment.updateFailReason(e.getMessage());
            throw new CustomException(ErrorCode.PAYMENT_FAILED);
        }
    }

    public CancelPaymentResponse cancelPayment(CancelPaymentRequest request) {
        Payment payment = paymentRepository.findByPaymentKey(request.getPaymentKey())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!payment.isCompleted()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        try {
            callTossPaymentsCancel(request.getPaymentKey(), request.getCancelReason());

            payment.updateStatus(PaymentStatus.CANCELED);

            Penalty penalty = payment.getPenalty();
            penalty.markAsPaid();

            return CancelPaymentResponse.from(payment, request.getCancelReason());

        } catch (Exception e) {
            log.error("Payment cancellation failed for paymentKey: {}", request.getPaymentKey(), e);
            throw new CustomException(ErrorCode.PAYMENT_FAILED);
        }
    }

    private Map<String, Object> callTossPaymentsConfirm(ConfirmPaymentRequest request) {
        HttpHeaders headers = createTossHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("paymentKey", request.getPaymentKey());
        requestBody.put("orderId", request.getOrderId());
        requestBody.put("amount", request.getAmount());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = TOSS_PAYMENTS_BASE_URL + "/confirm";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("토스페이먼츠 결제 승인 실패");
        }

        return response.getBody();
    }

    private Map<String, Object> callTossPaymentsCancel(String paymentKey, String cancelReason) {
        HttpHeaders headers = createTossHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("cancelReason", cancelReason);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String url = TOSS_PAYMENTS_BASE_URL + "/" + paymentKey + "/cancel";
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("토스페이먼츠 결제 취소 실패");
        }

        return response.getBody();
    }

    private HttpHeaders createTossHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String credentials = secretKey + ":";
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCredentials);

        return headers;
    }
}