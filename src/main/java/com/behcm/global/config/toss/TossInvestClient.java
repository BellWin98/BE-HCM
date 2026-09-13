package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.http.HttpMethod;
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
 * <p><b>공개 API 는 조회 전용이다.</b> 토스 Open API 에는 주문 생성·정정·취소가 포함되어 있으나
 * 이 클래스가 {@code public} 으로 노출하는 것은 GET 뿐이다 — 실수로도 주문이 나가지 않게 하기 위한
 * 의도적인 제약이다.
 *
 * <p>주문 기능이 추가되며 쓰기 경로가 생겼지만 그 제약은 없어진 게 아니라 <b>좁아졌다</b>:
 * {@link #post}는 package-private 이고, 호출자는 같은 패키지의 {@link TossOrderClient} 하나뿐이며,
 * 그 컴포넌트는 ADMIN 만 통과하는 컨트롤러({@code TossAccessChecker#canTrade}) 뒤에서만 쓰인다.
 * 즉 경계를 주석이 아니라 <b>컴파일러가</b> 지킨다 — 도메인 패키지에서는 POST 를 부를 방법이 없다.
 * 새 쓰기 경로가 필요하면 이 패키지 안에 두고 같은 인가 뒤에 붙여야 한다.
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
        return execute(owner, HttpMethod.GET, path, queryParams, null, accountSeq, false, true, false, 0);
    }

    /**
     * 쓰기 요청. <b>package-private 이다</b> — {@link TossOrderClient} 만 호출할 수 있다(클래스 javadoc 참조).
     *
     * <p>재시도 정책이 GET 과 다르다:
     * <ul>
     *   <li><b>401</b>: 토큰 만료다. 요청이 원장에 닿기 전에 거부된 것이라 재발급 후 1회 재시도해도 안전하다.</li>
     *   <li><b>429</b>: {@code idempotent} 일 때만 재시도한다. 멱등키({@code clientOrderId}) 없이 재시도하면
     *       한도 응답과 실제 접수가 엇갈렸을 때 주문이 두 번 들어간다.</li>
     *   <li><b>IO 예외(타임아웃)</b>: 재시도하지 않는다. 이미 접수됐을 수 있다.</li>
     * </ul>
     *
     * @param idempotent 본문에 멱등키가 실려 있어 같은 요청을 다시 보내도 안전한지
     */
    JsonNode post(TossAccountOwner owner, String path, Object body, Long accountSeq, boolean idempotent) {
        return execute(owner, HttpMethod.POST, path, Map.of(), body, accountSeq, true, idempotent, false, 0);
    }

    /**
     * @param writePath    주문 경로인지. 에러를 {@link TossOrderErrorMapper} 로 세분화할지 결정한다.
     * @param retryOnLimit 429 를 재시도해도 되는지
     */
    private JsonNode execute(
            TossAccountOwner owner,
            HttpMethod method,
            String path,
            Map<String, String> queryParams,
            Object body,
            Long accountSeq,
            boolean writePath,
            boolean retryOnLimit,
            boolean isAuthRetry,
            int rateLimitAttempt
    ) {
        String accessToken = tokenStore.getAccessToken(owner, this::issueAccessToken);

        ResponseEntity<String> response;
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
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
                    });

            if (body != null) {
                request = request.contentType(MediaType.APPLICATION_JSON).body(body);
            }

            response = request
                    .retrieve()
                    // 기본 예외 변환을 끄고 상태코드와 본문을 직접 다룬다.
                    // 토스는 에러 본문에 code/requestId 를 주므로 그대로 버리면 원인 추적이 불가능하다.
                    .onStatus(status -> true, (req, clientResponse) -> { })
                    .toEntity(String.class);
        } catch (Exception e) {
            // 여기서 재시도하지 않는다 — 쓰기 요청이라면 이미 접수됐을 수 있다(소켓 타임아웃 5초).
            if (writePath) {
                // 주문이 접수됐는지 알 수 없는 상태다. 사람이 미체결 내역을 확인해야 한다.
                log.error("Toss order call failed with unknown outcome: {} {} (owner={})", method, path, owner, e);
            } else {
                log.warn("Toss API call failed: {} {} (owner={}) - {}", method, path, owner, e.toString());
            }
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }

        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        String responseBody = response.getBody();

        if (response.getStatusCode().is2xxSuccessful()) {
            return unwrapResult(responseBody, path);
        }

        // 토큰 만료 — 캐시를 비우고 한 번만 재발급 후 재시도한다.
        // 401 은 요청이 처리되기 전에 거부된 것이므로 쓰기 경로에서도 안전하다.
        if (status == HttpStatus.UNAUTHORIZED && !isAuthRetry) {
            log.info("Toss API returned 401, refreshing token (owner={})", owner);
            tokenStore.evict(owner);
            return execute(owner, method, path, queryParams, body, accountSeq,
                    writePath, retryOnLimit, true, rateLimitAttempt);
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS && retryOnLimit && rateLimitAttempt < MAX_RATE_LIMIT_RETRIES) {
            backoff(response, owner, path);
            return execute(owner, method, path, queryParams, body, accountSeq,
                    writePath, true, isAuthRetry, rateLimitAttempt + 1);
        }

        throw toException(status, responseBody, path, owner, writePath);
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
     *
     * <p>주문 경로는 상태코드만으로 부족하다 — 잔고 부족과 장 마감과 거래정지가 전부 422 로 오는데
     * 사용자가 다음에 할 행동은 셋 다 다르다. 그래서 {@code code} 까지 보고
     * {@link TossOrderErrorMapper} 로 세분화한다. 토스의 원문 메시지는 로그에만 남기고
     * 사용자 응답에는 싣지 않는다(문구가 바뀌면 화면이 함께 흔들리고 내부 용어가 샌다).
     */
    private CustomException toException(
            HttpStatus status, String body, String path, TossAccountOwner owner, boolean writePath) {
        String code = "";
        String message = "";
        String requestId = "";
        String data = "";
        if (body != null && !body.isBlank()) {
            try {
                JsonNode error = objectMapper.readTree(body).path("error");
                code = error.path("code").asString("");
                message = error.path("message").asString("");
                requestId = error.path("requestId").asString("");
                JsonNode dataNode = error.get("data");
                // 호가 단위(tickSize/nearestPrices) 같은 부가 정보는 화면으로 내보내지 않고 여기 남긴다.
                data = dataNode == null || dataNode.isNull() ? "" : dataNode.toString();
            } catch (Exception e) {
                log.debug("Failed to parse Toss error body for {}", path);
            }
        }
        log.atLevel(errorLevel(status, code, writePath))
                .log("Toss API error: path={}, owner={}, status={}, code={}, requestId={}, message={}, data={}",
                        path, owner, status, code, requestId, message, data);

        if (writePath) {
            return new CustomException(TossOrderErrorMapper.toErrorCode(status, code));
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new CustomException(ErrorCode.TOSS_RATE_LIMITED);
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return new CustomException(ErrorCode.TOSS_UNAUTHORIZED);
        }
        return new CustomException(ErrorCode.TOSS_API_FAILED);
    }

    /**
     * 토스 에러의 로그 레벨. 전부 ERROR 로 찍으면 "잔고 부족"이 알림을 울린다.
     * <ul>
     *   <li>401/403 — 자격증명·권한 문제. 우리가 고쳐야 한다 → ERROR</li>
     *   <li>429, 점검(maintenance) — 토스 사정 → WARN</li>
     *   <li>주문 경로의 400/409/422 — 사용자가 고칠 것(잔고·장 마감·가격) → INFO</li>
     *   <li>조회 경로의 4xx, 그 외 5xx — 우리 요청이 틀렸거나 토스 장애 → ERROR</li>
     * </ul>
     */
    private static Level errorLevel(HttpStatus status, String code, boolean writePath) {
        if (status == HttpStatus.TOO_MANY_REQUESTS || "maintenance".equals(code)) {
            return Level.WARN;
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return Level.ERROR;
        }
        if (writePath && status != null && status.is4xxClientError()) {
            return Level.INFO;
        }
        return Level.ERROR;
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
