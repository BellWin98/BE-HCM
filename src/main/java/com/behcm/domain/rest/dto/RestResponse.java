package com.behcm.domain.rest.dto;

import com.behcm.domain.rest.entity.Rest;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class RestResponse {

    private Long id;
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;

    public static RestResponse from(Rest rest) {
        return RestResponse.builder()
                .id(rest.getId())
                .reason(rest.getReason())
                .startDate(rest.getStartDate())
                .endDate(rest.getEndDate())
                .build();
    }
}
