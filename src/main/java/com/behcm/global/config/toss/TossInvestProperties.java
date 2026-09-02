package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 토스증권 Open API 설정.
 *
 * <pre>
 * toss-invest:
 *   api:
 *     base-url: https://openapi.tossinvest.com
 *   accounts:
 *     - owner: ME
 *       client-id: ENC(...)
 *       client-secret: ENC(...)
 * </pre>
 *
 * 계좌를 추가할 때는 accounts 에 블록만 덧붙이면 되고 코드 수정은 필요 없다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "toss-invest")
public class TossInvestProperties {

    private Api api = new Api();
    private List<AccountCredentials> accounts = new ArrayList<>();

    @Getter
    @Setter
    public static class Api {
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class AccountCredentials {
        private TossAccountOwner owner;
        private String clientId;
        private String clientSecret;
    }

    /**
     * 소유자의 자격증명을 찾는다. 설정되지 않은 소유자(예: 아직 연동 전인 아빠)는
     * 500 이 아니라 404 로 응답하도록 전용 에러코드를 던진다.
     */
    public AccountCredentials credentialsOf(TossAccountOwner owner) {
        return accounts.stream()
                .filter(account -> account.getOwner() == owner)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.TOSS_ACCOUNT_NOT_CONFIGURED));
    }

    /**
     * 설정된 소유자 목록. 프론트의 계좌 전환 세그먼트를 채우는 데 쓴다.
     */
    public List<TossAccountOwner> configuredOwners() {
        return accounts.stream()
                .map(AccountCredentials::getOwner)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }
}
