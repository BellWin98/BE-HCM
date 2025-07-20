package com.behcm.domain.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
public class RestRequest {
    
    @NotBlank(message = "휴식 사유는 필수입니다.")
    private String reason;
    
    @NotBlank(message = "시작일은 필수입니다.")
    private String startDate;
    
    @NotBlank(message = "종료일은 필수입니다.")
    private String endDate;
}