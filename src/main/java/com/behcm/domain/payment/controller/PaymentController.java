package com.behcm.domain.payment.controller;

import com.behcm.domain.payment.dto.*;
import com.behcm.domain.payment.service.TossPaymentService;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 API")
@Slf4j
public class PaymentController {

    private final TossPaymentService tossPaymentService;

    @PostMapping("/orders")
    @Operation(summary = "결제 주문 생성", description = "벌금 납부를 위한 결제 주문을 생성합니다.")
    public ResponseEntity<ApiResponse<CreatePaymentOrderResponse>> createPaymentOrder(
            @Valid @RequestBody CreatePaymentOrderRequest request) {

        log.info("Creating payment order for penaltyRecordId: {}, amount: {}, orderId: {}",
                request.getPenaltyRecordId(), request.getAmount(), request.getOrderId());

        CreatePaymentOrderResponse response = tossPaymentService.createPaymentOrder(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/confirm")
    @Operation(summary = "결제 승인", description = "토스페이먼츠에서 결제를 승인합니다.")
    public ResponseEntity<ApiResponse<ConfirmPaymentResponse>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request) {

        log.info("Confirming payment for paymentKey: {}, orderId: {}, amount: {}",
                request.getPaymentKey(), request.getOrderId(), request.getAmount());

        ConfirmPaymentResponse response = tossPaymentService.confirmPayment(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/cancel")
    @Operation(summary = "결제 취소", description = "승인된 결제를 취소합니다.")
    public ResponseEntity<ApiResponse<CancelPaymentResponse>> cancelPayment(
            @Valid @RequestBody CancelPaymentRequest request) {

        log.info("Canceling payment for paymentKey: {}, reason: {}",
                request.getPaymentKey(), request.getCancelReason());

        CancelPaymentResponse response = tossPaymentService.cancelPayment(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}