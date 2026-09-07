package com.behcm.domain.tossstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 주문 요청.
 *
 * <p>여기 애노테이션은 <b>형식</b>만 본다(있는가, 모양이 맞는가). 조합 규칙(지정가인데 가격이 없다,
 * 국내인데 LOC 이다 …)은 종목 정보를 알아야 판정할 수 있어 {@code TossOrderValidator} 가 맡는다.
 *
 * <p>수치를 {@code String} 으로 받는 이유는 토스가 그렇게 받기 때문이다 — double 로 받으면
 * 70000.00000000001 같은 값이 만들어져 호가 단위 검사에 걸린다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TossOrderRequest {

    @NotBlank(message = "계좌 소유자는 필수입니다.")
    private String owner;

    @NotBlank(message = "종목 코드는 필수입니다.")
    @Pattern(regexp = "^[A-Za-z0-9.\\-]{1,20}$", message = "종목 코드 형식이 올바르지 않습니다.")
    private String symbol;

    @NotBlank(message = "매수/매도 구분은 필수입니다.")
    @Pattern(regexp = "^(BUY|SELL)$", message = "매수/매도 구분이 올바르지 않습니다.")
    private String side;

    @NotBlank(message = "주문 유형은 필수입니다.")
    @Pattern(regexp = "^(LIMIT|MARKET)$", message = "주문 유형이 올바르지 않습니다.")
    private String orderType;

    /**
     * 미전달 시 DAY. {@code CLS} 와 {@code LIMIT} 의 조합이 LOC 이며 미국 주식에만 쓸 수 있다.
     * {@code OPG}(국내 시가단일가)는 이번 범위가 아니라 받지 않는다.
     */
    @Pattern(regexp = "^(DAY|CLS)$", message = "주문 유효 조건이 올바르지 않습니다.")
    private String timeInForce;

    @NotBlank(message = "주문 수량은 필수입니다.")
    @Pattern(regexp = "^[0-9]+$", message = "주문 수량은 1주 이상의 정수여야 합니다.")
    private String quantity;

    /** 지정가에서만 쓴다. 시장가에 실어 보내면 토스가 거부한다. */
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,4})?$", message = "주문 가격 형식이 올바르지 않습니다.")
    private String price;

    /**
     * 멱등키. 프론트가 만들어 보낸다 — 서버가 만들면 재시도마다 값이 달라져 멱등성이 사라진다.
     * 토스 기준 10분간 유효하며, 같은 키로 내용만 다른 주문을 보내면 422 가 난다.
     */
    @Pattern(regexp = "^[a-zA-Z0-9\\-_]{1,36}$", message = "주문 식별자 형식이 올바르지 않습니다.")
    private String clientOrderId;

    /** 1억원 이상 주문에 필요한 사용자 확인. 화면에서 체크박스로 받는다. */
    private boolean confirmHighValueOrder;
}
