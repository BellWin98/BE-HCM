package com.behcm.domain.rest.dto;

import com.behcm.domain.rest.entity.RestInfo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class RestInfoResponse {

    private Long id;
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;

    public static RestInfoResponse from(RestInfo restInfo) {
        return RestInfoResponse.builder()
                .id(restInfo.getId())
                .reason(restInfo.getReason())
                .startDate(restInfo.getStartDate())
                .endDate(restInfo.getEndDate())
                .build();
    }
}
