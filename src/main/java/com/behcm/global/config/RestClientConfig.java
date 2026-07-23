package com.behcm.global.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.security.cert.X509Certificate;

@Configuration
public class RestClientConfig {

    @Bean
    @Profile("local")
    public RestClient localRestClient(RestClient.Builder builder) throws Exception {
        // 모든 인증서를 신뢰하도록 TrustStrategy 구현
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        // SSLContext 생성
        var sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();

        // 호스트네임 검증 무시. 검증기만 지정하면 정책이 BOTH 로 잡혀 JDK 내장 검증이 함께 돌아
        // 결국 거부되므로, 정책을 CLIENT 로 낮춰 아래 검증기만 사용하게 한다.
        TlsSocketStrategy tlsSocketStrategy = ClientTlsStrategyBuilder.create()
                .setSslContext(sslContext)
                .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                .setHostnameVerifier((host, session) -> true)
                .buildClassic();

        // ConnectionManager에 SSL 적용.
        // 요청 팩토리를 직접 지정하면 spring.http.clients.* 프로퍼티가 적용되지 않으므로
        // 타임아웃은 여기서 httpclient5 설정으로 직접 맞춘다(dev/prod 와 동일한 5초).
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsSocketStrategy)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setSocketTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();

        // HttpClient 생성
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        return builder
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    @Profile({"dev", "prod"})
    public RestClient prodRestClient(RestClient.Builder builder) {
        // 타임아웃은 application.yml 의 spring.http.clients.* 로 설정한다.
        // (Boot 4.1 에서 ClientHttpRequestFactorySettings 는 HttpClientSettings 로 대체되었고,
        //  자동 구성된 RestClient.Builder 가 해당 프로퍼티를 이미 반영해 준다.)
        return builder.build();
    }
}
