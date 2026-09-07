package com.behcm.domain.tossstock.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 주문 취소 요청. 취소도 계좌 컨텍스트가 필요해 소유자를 받는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class TossOrderCancelRequest {

    @NotBlank(message = "계좌 소유자는 필수입니다.")
    private String owner;
}
