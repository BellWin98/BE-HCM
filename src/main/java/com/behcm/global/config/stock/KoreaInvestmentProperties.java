package com.behcm.global.config.stock;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "korea-investment.api")
public class KoreaInvestmentProperties {

    private String baseUrl;
    private String appKey;
    private String appSecret;
    private String accountNumber;
    private String accountProductCode;
}