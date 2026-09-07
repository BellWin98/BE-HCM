package com.behcm.domain.tossstock.service;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 종목 검색 랭킹.
 *
 * <p>토스 Open API 에는 검색 엔드포인트가 없어(스펙 전체에 "검색"이 없다) 유니버스를 우리가 들고
 * 직접 매긴다. 그래서 "무엇이 위로 오는가"가 곧 이 기능의 품질이다 —
 * `삼성`을 쳤을 때 삼성전자가 아니라 삼성전자우가 먼저 나오면 쓸 수 없는 검색이 된다.
 */
class TossStockSearchServiceTest {

    private TossStockUniverse universe;
    private TossStockSearchService searchService;

    @BeforeEach
    void setUp() {
        universe = new TossStockUniverse();
        searchService = new TossStockSearchService(universe);
    }

    private TossListedStock kr(String symbol, String name, String securityType, boolean commonShare) {
        return TossListedStock.of(symbol, name, "KOSPI", securityType, commonShare);
    }

    private TossListedStock us(String symbol, String name) {
        return TossListedStock.of(symbol, name, "NASDAQ", "STOCK", true);
    }

    private void load(TossListedStock... entries) {
        universe.replace(List.of(entries));
    }

    private List<String> symbols(List<TossListedStock> results) {
        return results.stream().map(TossListedStock::symbol).toList();
    }

    @Test
    @DisplayName("심볼 완전일치가 이름 일치보다 먼저 온다")
    void exactSymbolMatchRanksFirst() {
        load(
                kr("000660", "SK하이닉스", "STOCK", true),
                kr("005930", "삼성전자", "STOCK", true),
                kr("005935", "005930 우선주 흉내", "STOCK", false)
        );

        assertThat(symbols(searchService.search("005930", 20))).startsWith("005930");
    }

    @Test
    @DisplayName("종목명 접두 일치가 부분 일치보다 먼저 온다")
    void namePrefixRanksAboveContains() {
        load(
                kr("111111", "대한삼성물산", "STOCK", true),
                kr("005930", "삼성전자", "STOCK", true)
        );

        assertThat(symbols(searchService.search("삼성", 20))).containsExactly("005930", "111111");
    }

    @Test
    @DisplayName("보통주를 우선주보다 먼저 보여준다")
    void commonShareRanksAbovePreferred() {
        // 이름이 거의 같아 점수가 같은 두 종목에서, 사려는 쪽은 대개 보통주다.
        load(
                kr("005935", "삼성전자우", "STOCK", false),
                kr("005930", "삼성전자", "STOCK", true)
        );

        assertThat(symbols(searchService.search("삼성전자", 20))).containsExactly("005930", "005935");
    }

    @Test
    @DisplayName("같은 점수면 이름이 짧은 쪽이 먼저 온다")
    void shorterNameWinsTies() {
        load(
                kr("111111", "삼성전자서비스홀딩스", "STOCK", true),
                kr("005930", "삼성전자", "STOCK", true)
        );

        assertThat(symbols(searchService.search("삼성전자", 20))).containsExactly("005930", "111111");
    }

    @Test
    @DisplayName("영문 심볼은 대소문자를 가리지 않는다")
    void symbolSearchIsCaseInsensitive() {
        load(us("AAPL", "애플"));

        assertThat(symbols(searchService.search("aapl", 20))).containsExactly("AAPL");
        assertThat(symbols(searchService.search("AaPl", 20))).containsExactly("AAPL");
    }

    @Test
    @DisplayName("질의의 앞뒤·중간 공백을 무시한다")
    void ignoresWhitespaceInQuery() {
        load(kr("005930", "삼성전자", "STOCK", true));

        assertThat(symbols(searchService.search("  삼성 전자 ", 20))).containsExactly("005930");
    }

    @Test
    @DisplayName("결과를 limit 개수로 자른다")
    void capsResultsAtLimit() {
        TossListedStock[] many = new TossListedStock[50];
        for (int i = 0; i < 50; i++) {
            many[i] = kr("%06d".formatted(i), "삼성계열 %d".formatted(i), "STOCK", true);
        }
        load(many);

        assertThat(searchService.search("삼성", 5)).hasSize(5);
    }

    @Test
    @DisplayName("일치하는 종목이 없으면 빈 목록을 준다")
    void returnsEmptyWhenNothingMatches() {
        load(kr("005930", "삼성전자", "STOCK", true));

        assertThat(searchService.search("존재하지않는종목", 20)).isEmpty();
    }

    @Test
    @DisplayName("빈 질의는 조회하지 않고 빈 목록을 준다")
    void blankQueryReturnsEmpty() {
        load(kr("005930", "삼성전자", "STOCK", true));

        assertThat(searchService.search("   ", 20)).isEmpty();
        assertThat(searchService.search(null, 20)).isEmpty();
    }

    @Test
    @DisplayName("유니버스가 아직 준비되지 않았으면 빈 결과가 아니라 준비 중 에러를 낸다")
    void notReadyUniverseFailsLoudly() {
        // 빈 배열을 주면 화면이 "그런 종목 없음"으로 읽는다 — 사실은 아직 못 받은 것이다.
        assertThatThrownBy(() -> searchService.search("삼성", 20))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOSS_STOCK_UNIVERSE_NOT_READY);
    }

    @Test
    @DisplayName("국내 종목은 LOC 를 지원하지 않고 미국 종목은 지원한다")
    void locSupportFollowsMarketCountry() {
        // 화면이 LOC 버튼을 그릴지 정하는 유일한 근거다. 토스는 CLS 를 미국 지정가에만 허용한다.
        load(kr("005930", "삼성전자", "STOCK", true), us("AAPL", "애플"));

        TossListedStock samsung = searchService.search("005930", 1).get(0);
        TossListedStock apple = searchService.search("AAPL", 1).get(0);

        assertThat(samsung.marketCountry()).isEqualTo("KR");
        assertThat(samsung.currency()).isEqualTo("KRW");
        assertThat(samsung.locSupported()).isFalse();

        assertThat(apple.marketCountry()).isEqualTo("US");
        assertThat(apple.currency()).isEqualTo("USD");
        assertThat(apple.locSupported()).isTrue();
    }

    @Test
    @DisplayName("ETF 도 검색된다")
    void findsEtfs() {
        load(kr("069500", "KODEX 200", "ETF", true));

        assertThat(symbols(searchService.search("KODEX", 20))).containsExactly("069500");
    }
}
