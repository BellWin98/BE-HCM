package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossPortfolioResponse;
import com.behcm.domain.tossstock.dto.TossRealizedProfitRequest;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse.CurrencyTotals;
import com.behcm.domain.tossstock.service.TossOrderHistoryReader.OrderHistory;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import com.behcm.global.config.toss.TossInvestProperties;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossStockServiceTest {

    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final Long ACCOUNT_SEQ = 1L;
    private static final String BUYING_POWER_PATH = "/api/v1/buying-power";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TossInvestClient tossInvestClient;
    @Mock
    private TossInvestProperties properties;
    @Mock
    private TossAccountResolver accountResolver;
    @Mock
    private TossHoldingsReader holdingsReader;
    @Mock
    private TossOrderHistoryReader orderHistoryReader;
    @Mock
    private TossExchangeRateReader exchangeRateReader;

    private TossStockService service;

    @BeforeEach
    void setUp() {
        // 실현손익 계산기는 실제 구현을 쓴다 — 이 서비스의 핵심 동작이 계산 결과의 조립이기 때문이다.
        service = new TossStockService(
                tossInvestClient, properties, accountResolver,
                holdingsReader, orderHistoryReader, exchangeRateReader, new TossRealizedProfitCalculator()
        );
        given(accountResolver.resolveAccountSeq(OWNER)).willReturn(ACCOUNT_SEQ);
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    private static final String DOMESTIC_HOLDINGS = """
            {
              "totalPurchaseAmount": {"krw": "6500000", "usd": null},
              "marketValue": {
                "amount": {"krw": "7200000", "usd": null},
                "amountAfterCost": {"krw": "7180000", "usd": null}
              },
              "profitLoss": {
                "amount": {"krw": "700000", "usd": null},
                "amountAfterCost": {"krw": "680000", "usd": null},
                "rate": "0.1516",
                "rateAfterCost": "0.1406"
              },
              "dailyProfitLoss": {
                "amount": {"krw": "130000", "usd": null},
                "rate": "0.0185"
              },
              "items": [
                {
                  "symbol": "005930",
                  "name": "삼성전자",
                  "marketCountry": "KR",
                  "currency": "KRW",
                  "quantity": "100",
                  "lastPrice": "72000",
                  "averagePurchasePrice": "65000",
                  "marketValue": {"purchaseAmount": "6500000", "amount": "7200000", "amountAfterCost": "7180000"},
                  "profitLoss": {
                    "amount": "700000", "amountAfterCost": "680000",
                    "rate": "0.1077", "rateAfterCost": "0.0846"
                  },
                  "dailyProfitLoss": {"amount": "100000", "rate": "0.0141"},
                  "cost": {"commission": "1080", "tax": null}
                }
              ]
            }
            """;

    /** 국내는 손실(-13,100원), 해외는 이익(+$110.40)이라 전체 환산 손익률만 플러스가 되는 계좌. */
    private static final String MIXED_HOLDINGS = """
            {
              "totalPurchaseAmount": {"krw": "2754400", "usd": "2290.20"},
              "marketValue": {
                "amount": {"krw": "2741300", "usd": "2400.60"},
                "amountAfterCost": {"krw": "2735000", "usd": "2394.10"}
              },
              "profitLoss": {
                "amount": {"krw": "-13100", "usd": "110.40"},
                "amountAfterCost": {"krw": "-19400", "usd": "104.90"},
                "rate": "0.0236",
                "rateAfterCost": "0.0212"
              },
              "dailyProfitLoss": {
                "amount": {"krw": "18700", "usd": "21.30"},
                "rate": "0.0080"
              },
              "items": [
                {
                  "symbol": "005930",
                  "name": "삼성전자",
                  "marketCountry": "KR",
                  "currency": "KRW",
                  "quantity": "12",
                  "lastPrice": "73400",
                  "averagePurchasePrice": "68200",
                  "marketValue": {"purchaseAmount": "818400", "amount": "880800", "amountAfterCost": "878200"},
                  "profitLoss": {
                    "amount": "62400", "amountAfterCost": "60290",
                    "rate": "0.0763", "rateAfterCost": "0.0737"
                  },
                  "dailyProfitLoss": {"amount": "-3500", "rate": "-0.0040"},
                  "cost": {"commission": "2110", "tax": null}
                },
                {
                  "symbol": "NVDA",
                  "name": "엔비디아",
                  "marketCountry": "US",
                  "currency": "USD",
                  "quantity": "8",
                  "lastPrice": "141.60",
                  "averagePurchasePrice": "118.20",
                  "marketValue": {"purchaseAmount": "945.60", "amount": "1132.80", "amountAfterCost": "1127.30"},
                  "profitLoss": {
                    "amount": "187.20", "amountAfterCost": "183.90",
                    "rate": "0.1980", "rateAfterCost": "0.1945"
                  },
                  "dailyProfitLoss": {"amount": "37.20", "rate": "0.0340"},
                  "cost": {"commission": "5.50", "tax": null}
                }
              ]
            }
            """;

    private static final String USD_KRW_RATE = """
            {
              "baseCurrency": "USD",
              "quoteCurrency": "KRW",
              "rate": "1382.4",
              "midRate": "1375",
              "basisPoint": "53.8",
              "rateChangeType": "UP",
              "validFrom": "2026-09-02T14:07:00+09:00",
              "validUntil": "2026-09-02T14:08:00+09:00"
            }
            """;

    @Test
    @DisplayName("해외 종목이 있으면 환율로 원화 환산 합계를 채운다")
    void getPortfolio_withUsdHoldings_addsKrwConvertedTotals() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(MIXED_HOLDINGS));
        given(exchangeRateReader.readUsdToKrw(OWNER)).willReturn(json(USD_KRW_RATE));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        assertThat(portfolio.getUsdKrwRate()).isEqualByComparingTo("1382.4");
        assertThat(portfolio.getUsdKrwMidRate()).isEqualByComparingTo("1375");
        assertThat(portfolio.getUsdKrwRateChangeType()).isEqualTo("UP");
        assertThat(portfolio.getUsdKrwRateAsOf()).isEqualTo("2026-09-02T14:07:00+09:00");

        // 2,741,300 + 2,400.60 × 1,382.4 = 6,059,889.44
        assertThat(portfolio.getTotalMarketValueInKrw()).isEqualByComparingTo("6059889");
        assertThat(portfolio.getTotalPurchaseAmountInKrw()).isEqualByComparingTo("5920372");
        // 국내가 -13,100 이어도 해외 이익을 환산해 더하면 전체는 플러스다.
        assertThat(portfolio.getTotalProfitLossInKrw()).isEqualByComparingTo("139517");
        assertThat(portfolio.getTotalProfitLossAfterCostInKrw()).isEqualByComparingTo("125614");
        assertThat(portfolio.getDailyProfitLossInKrw()).isEqualByComparingTo("48145");
        assertThat(portfolio.getOverseasWeightPercent()).isEqualByComparingTo("54.76");
    }

    @Test
    @DisplayName("요약 세후 손익을 매핑한다")
    void getPortfolio_mapsOverviewAfterCostFigures() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(MIXED_HOLDINGS));
        given(exchangeRateReader.readUsdToKrw(OWNER)).willReturn(json(USD_KRW_RATE));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        assertThat(portfolio.getTotalMarketValueAfterCostKrw()).isEqualByComparingTo("2735000");
        assertThat(portfolio.getTotalMarketValueAfterCostUsd()).isEqualByComparingTo("2394.10");
        assertThat(portfolio.getTotalProfitLossAfterCostKrw()).isEqualByComparingTo("-19400");
        assertThat(portfolio.getTotalProfitLossAfterCostUsd()).isEqualByComparingTo("104.90");
        assertThat(portfolio.getTotalProfitLossRateAfterCost()).isEqualByComparingTo("2.12");
    }

    @Test
    @DisplayName("환율 조회가 실패하면 환산 합계를 null로 남기고 나머지는 그대로 반환한다")
    void getPortfolio_whenExchangeRateFails_leavesConvertedTotalsNull() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(MIXED_HOLDINGS));
        given(exchangeRateReader.readUsdToKrw(OWNER))
                .willThrow(new CustomException(ErrorCode.TOSS_API_FAILED));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        // 0 으로 채우면 해외 자산이 통째로 사라진 것처럼 보인다. 환산 불가는 null 이어야 한다.
        assertThat(portfolio.getUsdKrwRate()).isNull();
        assertThat(portfolio.getTotalMarketValueInKrw()).isNull();
        assertThat(portfolio.getTotalProfitLossInKrw()).isNull();
        assertThat(portfolio.getOverseasWeightPercent()).isNull();
        // 환산과 무관한 값들은 살아 있어야 한다.
        assertThat(portfolio.getHoldings()).hasSize(2);
        assertThat(portfolio.getTotalMarketValueKrw()).isEqualByComparingTo("2741300");
        assertThat(portfolio.getTotalMarketValueUsd()).isEqualByComparingTo("2400.60");
    }

    @Test
    @DisplayName("해외 종목이 없으면 환율을 조회하지 않고 국내 금액을 그대로 환산 합계로 쓴다")
    void getPortfolio_withoutUsdHoldings_skipsExchangeRateLookup() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(DOMESTIC_HOLDINGS));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        then(exchangeRateReader).should(never()).readUsdToKrw(any());
        // 환산할 것이 없으니 환율은 없지만, 합계 자체는 국내 금액으로 확정된다.
        assertThat(portfolio.getUsdKrwRate()).isNull();
        assertThat(portfolio.getTotalMarketValueInKrw()).isEqualByComparingTo("7200000");
        assertThat(portfolio.getTotalProfitLossInKrw()).isEqualByComparingTo("700000");
        assertThat(portfolio.getOverseasWeightPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("손익률을 소수비율에서 퍼센트로 변환한다")
    void getPortfolio_convertsRatioRatesToPercent() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(DOMESTIC_HOLDINGS));
        given(tossInvestClient.get(eq(OWNER), eq(BUYING_POWER_PATH), any(), eq(ACCOUNT_SEQ)))
                .willReturn(json("""
                        {"currency": "KRW", "cashBuyingPower": "1500000"}
                        """));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        // 0.1516 을 그대로 쓰면 화면에 0.15% 로 뜬다 — 100배 변환이 핵심이다.
        assertThat(portfolio.getTotalProfitLossRate()).isEqualByComparingTo("15.16");
        assertThat(portfolio.getDailyProfitLossRate()).isEqualByComparingTo("1.85");
        assertThat(portfolio.getHoldings().get(0).getProfitLossRate()).isEqualByComparingTo("10.77");
        assertThat(portfolio.getHoldings().get(0).getProfitLossRateAfterCost()).isEqualByComparingTo("8.46");
    }

    @Test
    @DisplayName("종목별 일간 손익률을 보유주식 응답에서 그대로 채운다")
    void getPortfolio_fillsDailyChangeRateWithoutExtraCalls() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(DOMESTIC_HOLDINGS));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        // 한투는 종목마다 현재가 API 를 따로 호출해야 했지만 토스는 보유주식 응답에 들어 있다.
        assertThat(portfolio.getHoldings().get(0).getDailyProfitLossRate()).isEqualByComparingTo("1.41");
        assertThat(portfolio.getHoldings().get(0).getDailyProfitLoss()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("해외 종목이 없으면 usd 합계는 null로 남기고 USD 매수가능금액을 조회하지 않는다")
    void getPortfolio_withoutUsdHoldings_leavesUsdNull() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(DOMESTIC_HOLDINGS));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        // 0 으로 채우면 "해외 자산 0원"으로 오해한다. 미보유는 null 이어야 한다.
        assertThat(portfolio.getTotalMarketValueUsd()).isNull();
        assertThat(portfolio.getCashBuyingPowerUsd()).isNull();
        assertThat(portfolio.getTotalMarketValueKrw()).isEqualByComparingTo("7200000");
    }

    @Test
    @DisplayName("세금이 null인 종목은 0으로 다룬다")
    void getPortfolio_withNullTax_treatsAsZero() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(DOMESTIC_HOLDINGS));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        assertThat(portfolio.getHoldings().get(0).getTax()).isEqualByComparingTo("0");
        assertThat(portfolio.getHoldings().get(0).getCommission()).isEqualByComparingTo("1080");
    }

    @Test
    @DisplayName("매수가능금액 조회가 실패해도 보유 현황은 반환한다")
    void getPortfolio_whenBuyingPowerFails_stillReturnsHoldings() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json(DOMESTIC_HOLDINGS));
        given(tossInvestClient.get(eq(OWNER), eq(BUYING_POWER_PATH), any(), eq(ACCOUNT_SEQ)))
                .willThrow(new CustomException(ErrorCode.TOSS_API_FAILED));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        assertThat(portfolio.getHoldings()).hasSize(1);
        assertThat(portfolio.getCashBuyingPowerKrw()).isNull();
    }

    @Test
    @DisplayName("보유 종목이 없으면 빈 목록을 반환한다")
    void getPortfolio_withNoHoldings_returnsEmptyList() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json("""
                {
                  "totalPurchaseAmount": {"krw": "0", "usd": null},
                  "marketValue": {"amount": {"krw": "0", "usd": null}, "amountAfterCost": {"krw": "0", "usd": null}},
                  "profitLoss": {"amount": {"krw": "0", "usd": null}, "amountAfterCost": {"krw": "0", "usd": null},
                                 "rate": "0", "rateAfterCost": "0"},
                  "dailyProfitLoss": {"amount": {"krw": "0", "usd": null}, "rate": "0"},
                  "items": []
                }
                """));

        TossPortfolioResponse portfolio = service.getPortfolio(OWNER);

        assertThat(portfolio.getHoldings()).isEmpty();
        assertThat(portfolio.getOwnerName()).isEqualTo("나");
    }

    private Fill fill(String symbol, String currency, TradeSide side, String date,
                      String quantity, String amount, String commission, String tax) {
        return new Fill(symbol, currency, side, LocalDate.parse(date),
                new BigDecimal(quantity), new BigDecimal(amount), new BigDecimal(commission), new BigDecimal(tax));
    }

    private TossRealizedProfitRequest request(String startDate, String endDate) {
        TossRealizedProfitRequest request = new TossRealizedProfitRequest();
        request.setOwner("ME");
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    private void stubHistory(List<Fill> fills, Map<String, String> names) {
        given(orderHistoryReader.readAll(OWNER, ACCOUNT_SEQ)).willReturn(new OrderHistory(fills, names));
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json("""
                {"items": []}
                """));
    }

    @Test
    @DisplayName("조회 기간 밖의 체결은 결과에서 제외하되 원가 계산에는 반영한다")
    void getRealizedProfit_excludesFillsOutsidePeriodButKeepsTheirCostBasis() {
        stubHistory(List.of(
                fill("005930", "KRW", TradeSide.BUY, "2025-06-01", "10", "10000", "0", "0"),
                fill("005930", "KRW", TradeSide.SELL, "2026-02-10", "10", "15000", "0", "0")
        ), Map.of("005930", "삼성전자"));

        TossRealizedProfitResponse response = service.getRealizedProfit(request("2026-01-01", "2026-12-31"));

        // 매수는 기간 밖이라 목록에 없지만, 그 원가가 반영되어야 손익이 5000 으로 나온다.
        assertThat(response.getTrades()).hasSize(1);
        assertThat(response.getTrades().get(0).getTradeType()).isEqualTo("SELL");
        assertThat(response.getTrades().get(0).getProfitLoss()).isEqualByComparingTo("5000");
        assertThat(response.getTrades().get(0).getName()).isEqualTo("삼성전자");
        assertThat(response.isEstimated()).isFalse();
    }

    @Test
    @DisplayName("통화가 섞이면 합계를 통화별로 나눠서 반환한다")
    void getRealizedProfit_withMixedCurrencies_splitsTotalsPerCurrency() {
        stubHistory(List.of(
                fill("005930", "KRW", TradeSide.BUY, "2026-01-02", "10", "10000", "0", "0"),
                fill("005930", "KRW", TradeSide.SELL, "2026-01-10", "10", "12000", "0", "0"),
                fill("AAPL", "USD", TradeSide.BUY, "2026-01-02", "10", "1000", "0", "0"),
                fill("AAPL", "USD", TradeSide.SELL, "2026-01-10", "10", "1500", "0", "0")
        ), Map.of());

        TossRealizedProfitResponse response = service.getRealizedProfit(request("2026-01-01", "2026-01-31"));

        // 원화와 달러를 한 숫자로 더하면 조용히 틀린 금액이 나온다.
        assertThat(response.getTotals()).hasSize(2);
        CurrencyTotals krw = response.getTotals().stream()
                .filter(total -> "KRW".equals(total.getCurrency())).findFirst().orElseThrow();
        CurrencyTotals usd = response.getTotals().stream()
                .filter(total -> "USD".equals(total.getCurrency())).findFirst().orElseThrow();

        assertThat(krw.getTotalProfitLoss()).isEqualByComparingTo("2000");
        assertThat(krw.getTotalProfitLossRate()).isEqualByComparingTo("20.00");
        assertThat(usd.getTotalProfitLoss()).isEqualByComparingTo("500");
        assertThat(usd.getTotalProfitLossRate()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("종목명을 못 찾으면 심볼을 그대로 쓴다")
    void getRealizedProfit_withoutResolvedName_fallsBackToSymbol() {
        stubHistory(List.of(
                fill("005930", "KRW", TradeSide.BUY, "2026-01-02", "10", "10000", "0", "0")
        ), Map.of());

        TossRealizedProfitResponse response = service.getRealizedProfit(request("2026-01-01", "2026-01-31"));

        assertThat(response.getTrades().get(0).getName()).isEqualTo("005930");
    }

    @Test
    @DisplayName("체결 평균가는 체결금액을 수량으로 나눠 구한다")
    void getRealizedProfit_computesUnitPriceFromAmountAndQuantity() {
        stubHistory(List.of(
                fill("005930", "KRW", TradeSide.BUY, "2026-01-02", "8", "10000", "0", "0")
        ), Map.of());

        TossRealizedProfitResponse response = service.getRealizedProfit(request("2026-01-01", "2026-01-31"));

        assertThat(response.getTrades().get(0).getPrice()).isEqualByComparingTo("1250.00");
    }

    @Test
    @DisplayName("보유 평균단가로 원가를 메운 결과는 추정치로 표시한다")
    void getRealizedProfit_whenCostSeeded_marksResponseEstimated() {
        given(orderHistoryReader.readAll(OWNER, ACCOUNT_SEQ)).willReturn(new OrderHistory(List.of(
                fill("005930", "KRW", TradeSide.SELL, "2026-01-10", "10", "10000", "0", "0")
        ), Map.of()));
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ)).willReturn(json("""
                {"items": [{"symbol": "005930", "averagePurchasePrice": "500"}]}
                """));

        TossRealizedProfitResponse response = service.getRealizedProfit(request("2026-01-01", "2026-01-31"));

        assertThat(response.isEstimated()).isTrue();
        assertThat(response.getTrades().get(0).isEstimated()).isTrue();
        assertThat(response.getTrades().get(0).getProfitLoss()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 400으로 거부한다")
    void getRealizedProfit_withInvertedRange_throwsInvalidInput() {
        assertThatThrownBy(() -> service.getRealizedProfit(request("2026-02-01", "2026-01-01")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("날짜 형식이 잘못되면 400으로 거부한다")
    void getRealizedProfit_withMalformedDate_throwsInvalidInput() {
        assertThatThrownBy(() -> service.getRealizedProfit(request("2026-13-01", "2026-12-31")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("알 수 없는 소유자는 400으로 거부한다")
    void getRealizedProfit_withUnknownOwner_throwsInvalidInput() {
        TossRealizedProfitRequest request = request("2026-01-01", "2026-01-31");
        request.setOwner("UNCLE");

        assertThatThrownBy(() -> service.getRealizedProfit(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }
}
