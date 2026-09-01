package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 토스증권 Open API 호출 클라이언트.
 *
 * <p>한국투자증권 클라이언트와 분리한 이유:
 * <ul>
 *   <li>인증 방식이 다르다 — OAuth2 client_credentials, 소유자(client)마다 토큰이 별개다.</li>
 *   <li>계좌 지정이 쿼리 파라미터가 아니라 {@code X-Tossinvest-Account} 헤더다.</li>
 *   <li>응답이 {@code {"result": ...}} envelope 이고, 에러는 {@code {"error": {...}}} + HTTP 상태코드다.</li>
 * </ul>
 *
 * <p><b>조회 전용이다.</b> 토스 Open API 에는 주문 생성·정정·취소가 포함되어 있으나 이 클라이언트는
 * GET 만 노출한다 — 실수로도 주문이 나가지 않게 하기 위한 의도적인 제약이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossInvestClient {

    private final TossInvestProperties properties;
    private final RestClient restClient;
    private final TossTokenStore tokenStore;
    private final JsonMapper objectMapper = new JsonMapper();

    private static final String TOKEN_ENDPOINT = "/oauth2/token";
    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    /** 429 재시도 횟수 상한. 이 이상은 사용자에게 에러로 알리는 편이 낫다. */
    private static final int MAX_RATE_LIMIT_RETRIES = 2;
    /** Retry-After 를 그대로 신뢰하면 요청이 오래 매달릴 수 있어 상한을 둔다. */
    private static final long MAX_RETRY_BACKOFF_MS = 2_000L;

    /**
     * 계좌 컨텍스트가 필요 없는 조회(시세·종목정보 등).
     */
    public JsonNode get(TossAccountOwner owner, String path, Map<String, String> queryParams) {
        return get(owner, path, queryParams, null);
    }

    /**
     * 계좌 컨텍스트가 필요한 조회. accountSeq 는 {@code X-Tossinvest-Account} 헤더로 전달된다.
     *
     * @return 응답 envelope 에서 {@code result} 를 벗겨낸 노드
     */
    public JsonNode get(TossAccountOwner owner, String path, Map<String, String> queryParams, Long accountSeq) {
        return execute(owner, path, queryParams, accountSeq, false, 0);
    }

    private JsonNode execute(
            TossAccountOwner owner,
            String path,
            Map<String, String> queryParams,
            Long accountSeq,
            boolean isAuthRetry,
            int rateLimitAttempt
    ) {
        String accessToken = tokenStore.getAccessToken(owner, this::issueAccessToken);

        ResponseEntity<String> response;
        try {
            response = restClient.get()
                    .uri(properties.getApi().getBaseUrl(), uriBuilder -> {
                        uriBuilder.path(path);
                        // 값을 리터럴로 넘기면 URI 템플릿의 일부로 취급되어 쿼리 컴포넌트에서
                        // 합법인 문자(&, = 등)가 인코딩되지 않는다. URI 변수로 넘겨야 엄격히 인코딩된다.
                        Map<String, Object> values = new HashMap<>();
                        if (queryParams != null) {
                            queryParams.forEach((key, value) -> {
                                if (value == null) {
                                    return;
                                }
                                uriBuilder.queryParam(key, "{" + key + "}");
                                values.put(key, value);
                            });
                        }
                        return uriBuilder.build(values);
                    })
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
                        if (accountSeq != null) {
                            headers.set(ACCOUNT_HEADER, String.valueOf(accountSeq));
                        }
                    })
                    .retrieve()
                    // 기본 예외 변환을 끄고 상태코드와 본문을 직접 다룬다.
                    // 토스는 에러 본문에 code/requestId 를 주므로 그대로 버리면 원인 추적이 불가능하다.
                    .onStatus(status -> true, (request, clientResponse) -> { })
                    .toEntity(String.class);
        } catch (Exception e) {
            log.error("Toss API call failed: {} (owner={})", path, owner, e);
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }

        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        String body = response.getBody();

        if (response.getStatusCode().is2xxSuccessful()) {
            return unwrapResult(body, path);
        }

        // 토큰 만료 — 캐시를 비우고 한 번만 재발급 후 재시도한다.
        if (status == HttpStatus.UNAUTHORIZED && !isAuthRetry) {
            log.info("Toss API returned 401, refreshing token (owner={})", owner);
            tokenStore.evict(owner);
            return execute(owner, path, queryParams, accountSeq, true, rateLimitAttempt);
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS && rateLimitAttempt < MAX_RATE_LIMIT_RETRIES) {
            backoff(response, owner, path);
            return execute(owner, path, queryParams, accountSeq, isAuthRetry, rateLimitAttempt + 1);
        }

        throw toException(status, body, path, owner);
    }

    /**
     * 성공 응답의 {@code result} 를 꺼낸다. envelope 이 깨진 응답은 파싱 실패로 다루는 편이
     * 빈 화면을 조용히 보여주는 것보다 낫다.
     */
    private JsonNode unwrapResult(String body, String path) {
        if (body == null || body.isBlank()) {
            log.error("Toss API returned an empty body: {}", path);
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("Toss API returned an unparsable body: {}", path, e);
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }
        JsonNode result = root.get("result");
        if (result == null || result.isNull()) {
            log.error("Toss API response has no 'result' field: {}", path);
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }
        return result;
    }

    private void backoff(ResponseEntity<String> response, TossAccountOwner owner, String path) {
        long waitMs = Math.min(retryAfterMillis(response), MAX_RETRY_BACKOFF_MS);
        log.warn("Toss API rate limited, retrying in {}ms: {} (owner={})", waitMs, path, owner);
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.TOSS_RATE_LIMITED);
        }
    }

    private long retryAfterMillis(ResponseEntity<String> response) {
        String retryAfter = response.getHeaders().getFirst("Retry-After");
        if (retryAfter == null || retryAfter.isBlank()) {
            return 500L;
        }
        try {
            return Math.max(0L, Long.parseLong(retryAfter.trim())) * 1_000L;
        } catch (NumberFormatException e) {
            return 500L;
        }
    }

    /**
     * 토스 에러 envelope 을 우리 예외로 변환한다. requestId 는 CS 문의 시 필요하므로 로그에 남긴다.
     */
    private CustomException toException(HttpStatus status, String body, String path, TossAccountOwner owner) {
        String code = "";
        String requestId = "";
        if (body != null && !body.isBlank()) {
            try {
                JsonNode error = objectMapper.readTree(body).path("error");
                code = error.path("code").asString("");
                requestId = error.path("requestId").asString("");
            } catch (Exception e) {
                log.debug("Failed to parse Toss error body for {}", path);
            }
        }
        log.error("Toss API error: path={}, owner={}, status={}, code={}, requestId={}",
                path, owner, status, code, requestId);

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new CustomException(ErrorCode.TOSS_RATE_LIMITED);
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return new CustomException(ErrorCode.TOSS_UNAUTHORIZED);
        }
        return new CustomException(ErrorCode.TOSS_API_FAILED);
    }

    /**
     * 액세스 토큰을 발급한다.
     *
     * <p>토스는 client 당 유효한 토큰이 1개뿐이고 재발급 시 이전 토큰이 <b>즉시 무효화</b>되므로,
     * 이 메서드는 반드시 {@link TossTokenStore} 의 분산 락 안에서만 호출되어야 한다.
     * 동시에 두 번 호출되면 서로의 토큰을 무효화시켜 401 루프에 빠진다.
     */
    private TossTokenStore.IssuedToken issueAccessToken(TossAccountOwner owner) {
        TossInvestProperties.AccountCredentials credentials = properties.credentialsOf(owner);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", credentials.getClientId());
        form.add("client_secret", credentials.getClientSecret());

        try {
            String response = restClient.post()
                    .uri(properties.getApi().getBaseUrl() + TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(response);
            String accessToken = json.path("access_token").asString("");
            int expiresIn = json.path("expires_in").asInt(0);

            if (accessToken.isBlank() || expiresIn <= 0) {
                log.error("Toss token response is missing access_token/expires_in (owner={})", owner);
                throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
            }

            log.info("Issued new Toss access token (owner={}, expiresIn={}s)", owner, expiresIn);
            // 만료 직전 사용으로 401 이 나는 것을 막기 위해 캐시 TTL 을 5분 앞당긴다.
            return new TossTokenStore.IssuedToken(accessToken, Duration.ofSeconds(Math.max(60, expiresIn - 300)));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to issue Toss access token (owner={})", owner, e);
            throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
        }
    }
}
