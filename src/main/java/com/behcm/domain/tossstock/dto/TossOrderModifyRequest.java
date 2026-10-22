package com.behcm.domain.tossstock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 주문 정정 요청.
 *
 * <p>{@link TossOrderRequest} 와 같이 여기 애노테이션은 <b>형식</b>만 본다. 국내는 수량 필수·미국은 수량 불가
 * 같은 조합 규칙은 종목의 시장을 알아야 판정할 수 있어 {@code TossOrderValidator#validateModify} 가 맡는다.
 *
 * <p>{@code symbol} 을 받는 이유: 정정 규칙이 시장(KR/US)마다 다른데, 주문 식별자만으로는 시장을 알 수 없다.
 * 주문 상세를 조회해 알아낼 수도 있지만 그러면 정정마다 왕복이 하나 더 는다 — 시장은 변하지 않는 값이라
 * 화면이 이미 들고 있는 심볼로 판정하고, 심볼이 실제 주문과 어긋나면 토스가 거부한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TossOrderModifyRequest {

    @NotBlank(message = "계좌 소유자는 필수입니다.")
    private String owner;

    @NotBlank(message = "종목 코드는 필수입니다.")
    @Pattern(regexp = "^[A-Za-z0-9.\\-]{1,20}$", message = "종목 코드 형식이 올바르지 않습니다.")
    private String symbol;

    @NotBlank(message = "주문 유형은 필수입니다.")
    @Pattern(regexp = "^(LIMIT|MARKET)$", message = "주문 유형이 올바르지 않습니다.")
    private String orderType;

    /** 국내 주식만. 정정 후 수량이며 양의 정수여야 한다. 미국 주식은 가격만 정정할 수 있어 비워 둔다. */
    @Pattern(regexp = "^[0-9]{1,15}(\\.[0-9]{1,6})?$", message = "정정 수량 형식이 올바르지 않습니다.")
    private String quantity;

    /** 지정가에서만 쓴다. */
    @Pattern(regexp = "^[0-9]+(\\.[0-9]{1,4})?$", message = "정정 가격 형식이 올바르지 않습니다.")
    private String price;

    /** 1억원 이상이면 토스가 요구한다. */
    private boolean confirmHighValueOrder;
}
