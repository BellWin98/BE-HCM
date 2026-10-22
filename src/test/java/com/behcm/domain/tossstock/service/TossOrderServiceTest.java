package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOrderModifyRequest;
import com.behcm.domain.tossstock.dto.TossOrderModifyResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossOrderClient;
import com.behcm.global.config.toss.TossOrderClient.TossModifyCommand;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 주문 정정 오케스트레이션: 주문번호 형식 → 계좌 해석 → 검증 → 토스 호출.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossOrderServiceTest {

    private static final TossAccountOwner OWNER = TossAccountOwner.ME;

    @Mock
    private TossAccountResolver accountResolver;
    @Mock
    private TossOrderValidator orderValidator;
    @Mock
    private TossOrderClient tossOrderClient;

    @InjectMocks
    private TossOrderService service;

    private TossOrderModifyRequest request;

    @BeforeEach
    void setUp() {
        request = new TossOrderModifyRequest();
        request.setOwner("ME");
        request.setSymbol("005930");
        request.setOrderType("LIMIT");
        request.setQuantity("15");
        request.setPrice("71000");
        given(accountResolver.resolveAccountSeq(OWNER)).willReturn(7L);
    }

    @Test
    @DisplayName("검증을 통과한 정정을 소유자 계좌로 보내고 새 주문번호를 돌려준다")
    void modifyOrder_sendsValidatedCommandToOwnersAccount() {
        TossModifyCommand command = new TossModifyCommand("LIMIT", new BigDecimal("15"), new BigDecimal("71000"), false);
        given(orderValidator.validateModify(OWNER, request)).willReturn(command);
        given(tossOrderClient.modify(OWNER, 7L, "order_1-A", command)).willReturn("order-2");

        TossOrderModifyResponse response = service.modifyOrder("order_1-A", request);

        assertThat(response.orderId()).isEqualTo("order-2");
    }

    @Test
    @DisplayName("주문번호 형식이 이상하면 토스를 부르지 않고 400 으로 거절한다")
    void modifyOrder_withMalformedOrderId_rejectedBeforeCallingToss() {
        // 주문번호는 URI 경로에 들어간다. 이상한 값이 그대로 박히면 URI 템플릿이 깨져 500 이 난다.
        assertThatThrownBy(() -> service.modifyOrder("../orders", request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(tossOrderClient, never()).modify(any(), any(), any(), any());
    }
}
