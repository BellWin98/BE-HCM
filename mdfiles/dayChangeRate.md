# 주식 보유 종목 전일종가 대비 등락률 표시 기능 백엔드 구현 요구사항

## 개요
프론트엔드에서 주식 보유 종목의 현재가 아래에 전일종가 대비 등락률을 표시하는 기능을 구현했습니다. 백엔드 API가 다음 요구사항을 충족하는지 확인하고, 필요시 수정이 필요합니다.

## API 엔드포인트

### GET `/api/stock/portfolio`

**응답 본문 (Response Body):**
```json
{
  "data": {
    "totalMarketValue": 50000000,
    "totalBuyValue": 45000000,
    "totalProfitLoss": 5000000,
    "totalProfitLossRate": 11.11,
    "depositToday": 1000000,
    "depositD2": 2000000,
    "holdings": [
      {
        "stockCode": "005930",
        "stockName": "삼성전자",
        "quantity": 100,
        "averagePrice": 70000,
        "currentPrice": 75000,
        "purchasePrice": 7000000,
        "marketValue": 7500000,
        "profitLoss": 500000,
        "profitLossRate": 7.14,
        "sector": "전기·전자",
        "dayChangeRate": 2.5
      },
      {
        "stockCode": "000660",
        "stockName": "SK하이닉스",
        "quantity": 50,
        "averagePrice": 150000,
        "currentPrice": 145000,
        "purchasePrice": 7500000,
        "marketValue": 7250000,
        "profitLoss": -250000,
        "profitLossRate": -3.33,
        "sector": "반도체",
        "dayChangeRate": -1.2
      }
    ]
  }
}
```

## 필수 응답 필드 상세

### StockPortfolio (최상위 객체)
- `totalMarketValue` (number): 총 시가총액
- `totalBuyValue` (number): 총 매수 금액
- `totalProfitLoss` (number): 총 손익
- `totalProfitLossRate` (number): 총 손익률 (%)
- `depositToday` (number): 당일 입금 가능 금액
- `depositD2` (number): D+2 입금 가능 금액
- `holdings` (array): 보유 종목 배열

### StockHolding (holdings 배열의 각 항목)
- `stockCode` (string, required): 종목 코드 (예: "005930")
- `stockName` (string, required): 종목명 (예: "삼성전자")
- `quantity` (number, required): 보유 수량 (주 수)
- `averagePrice` (number, required): 평균 매수가 (원)
- `currentPrice` (number, required): 현재가 (원)
- `purchasePrice` (number, required): 총 매수 금액 (원)
- `marketValue` (number, required): 시가총액 (원)
- `profitLoss` (number, required): 손익 (원)
- `profitLossRate` (number, required): 손익률 (%)
- `sector` (string, required): 섹터 정보
- `dayChangeRate` (number, optional): **전일 종가 대비 변동률 (%)** ← **새로 추가 필요**

## 새로 추가해야 할 필드: dayChangeRate

### 필드 설명
- **필드명**: `dayChangeRate`
- **타입**: `number | null | undefined`
- **설명**: 전일 종가 대비 현재가의 변동률 (백분율, 소수점 포함)
- **계산 공식**: `((currentPrice - previousClosePrice) / previousClosePrice) * 100`
- **예시 값**:
  - 상승: `2.5` (2.5% 상승)
  - 하락: `-1.2` (-1.2% 하락)
  - 보합: `0.0` (0% 변동)

### 필수 요구사항

1. **데이터 제공**
   - [ ] 각 보유 종목(`holdings` 배열의 각 항목)에 `dayChangeRate` 필드를 추가해야 합니다
   - [ ] 전일 종가 데이터가 없는 경우 `null` 또는 필드를 생략할 수 있습니다 (프론트엔드에서 조건부 렌더링 처리)

2. **계산 방법**
   - [ ] 전일 종가(`previousClosePrice`)와 현재가(`currentPrice`)를 비교하여 변동률을 계산합니다
   - [ ] 변동률은 소수점 첫째 자리까지 표시합니다 (예: `2.5`, `-1.2`, `0.0`)
   - [ ] 상승은 양수, 하락은 음수로 표현합니다

3. **데이터 소스**
   - [ ] 한국투자증권 API 또는 다른 주식 시세 API에서 전일 종가 데이터를 가져와야 합니다
   - [ ] 실시간 시세 API에서 제공하는 전일 대비 변동률을 직접 사용할 수도 있습니다

4. **에러 처리**
   - [ ] 전일 종가 데이터를 가져올 수 없는 경우 (예: 신규 상장 종목, 휴장일 등) `null`을 반환하거나 필드를 생략합니다
   - [ ] API 호출 실패 시에도 다른 필드들은 정상적으로 반환하고, `dayChangeRate`만 `null`로 처리합니다

## 프론트엔드 사용 예시

프론트엔드에서는 다음과 같이 사용합니다:

```typescript
// API 호출
const portfolio = await api.getStockPortfolio() as StockPortfolio;

// 각 보유 종목의 등락률 표시
portfolio.holdings.forEach(holding => {
  if (holding.dayChangeRate != null) {
    // 등락률 표시 (예: "+2.5%" 또는 "-1.2%")
    const display = `${holding.dayChangeRate >= 0 ? '+' : ''}${holding.dayChangeRate.toFixed(1)}%`;
  }
});
```

## UI 표시 요구사항

프론트엔드에서 다음과 같이 표시됩니다:

1. **위치**: 현재가 아래에 표시됩니다
2. **형식**: 
   - 상승: 빨간색으로 표시 (예: `+2.5%`)
   - 하락: 파란색으로 표시 (예: `-1.2%`)
   - 보합: 회색으로 표시 (예: `0.0%`)
3. **조건부 렌더링**: `dayChangeRate`가 `null`이거나 `undefined`인 경우 표시하지 않습니다
4. **텍스트 크기**: 현재가보다 작은 크기(`text-sm`)로 표시됩니다

## 구현 예시 (참고용)

백엔드에서 다음과 같이 구현할 수 있습니다:

```python
# Python 예시 (FastAPI)
def calculate_day_change_rate(current_price: float, previous_close: float) -> Optional[float]:
    """전일 종가 대비 변동률 계산"""
    if previous_close == 0:
        return None
    return ((current_price - previous_close) / previous_close) * 100

# 각 종목에 대해
holding = {
    "stockCode": "005930",
    "stockName": "삼성전자",
    "currentPrice": 75000,
    # ... 기타 필드
    "dayChangeRate": calculate_day_change_rate(75000, 73200)  # 예: 2.46
}
```

```java
// Java 예시 (Spring Boot)
public Double calculateDayChangeRate(Double currentPrice, Double previousClose) {
    if (previousClose == null || previousClose == 0) {
        return null;
    }
    return ((currentPrice - previousClose) / previousClose) * 100;
}

// 각 종목에 대해
StockHolding holding = StockHolding.builder()
    .stockCode("005930")
    .stockName("삼성전자")
    .currentPrice(75000.0)
    // ... 기타 필드
    .dayChangeRate(calculateDayChangeRate(75000.0, 73200.0))  // 예: 2.46
    .build();
```

## 중요 체크리스트

### 1. 필드 추가
- [ ] `StockHolding` 응답 객체에 `dayChangeRate` 필드를 추가했습니다
- [ ] 필드는 `number | null | undefined` 타입으로 처리합니다 (optional)

### 2. 데이터 계산
- [ ] 전일 종가 데이터를 정확히 가져오고 있습니다
- [ ] 변동률 계산 공식이 올바릅니다: `((currentPrice - previousClosePrice) / previousClosePrice) * 100`
- [ ] 소수점 첫째 자리까지 반올림하여 반환합니다

### 3. 데이터 소스
- [ ] 한국투자증권 API 또는 다른 주식 시세 API에서 전일 종가를 가져오고 있습니다
- [ ] 실시간 시세 API에서 전일 대비 변동률을 직접 제공하는 경우, 해당 값을 사용합니다

### 4. 에러 처리
- [ ] 전일 종가 데이터가 없는 경우 `null`을 반환하거나 필드를 생략합니다
- [ ] API 호출 실패 시에도 다른 필드들은 정상적으로 반환합니다

### 5. 데이터 정확성
- [ ] 상승은 양수, 하락은 음수로 정확히 표현됩니다
- [ ] 계산된 값이 실제 시세와 일치하는지 확인합니다

## 테스트 케이스

다음 시나리오를 테스트해야 합니다:

1. **정상 케이스**: 전일 종가 데이터가 있는 경우
   - `dayChangeRate`가 올바르게 계산되어 반환되는지 확인
   - 상승/하락이 올바른 부호로 표시되는지 확인

2. **데이터 없음**: 전일 종가 데이터가 없는 경우
   - `dayChangeRate`가 `null`이거나 필드가 생략되는지 확인
   - 다른 필드들은 정상적으로 반환되는지 확인

3. **보합**: 전일 종가와 현재가가 동일한 경우
   - `dayChangeRate`가 `0.0`으로 반환되는지 확인

4. **신규 상장**: 신규 상장 종목의 경우
   - 전일 종가가 없으므로 `null`을 반환하는지 확인

## 추가 고려사항

1. **성능**: 각 종목마다 전일 종가를 조회하는 경우, 배치로 조회하여 성능을 최적화하는 것을 권장합니다.

2. **캐싱**: 전일 종가는 하루 동안 변하지 않으므로, 캐싱을 활용하여 API 호출을 최소화할 수 있습니다.

3. **인증**: API는 인증된 사용자만 접근 가능해야 하며, 해당 사용자의 보유 종목만 반환해야 합니다.

4. **에러 처리**: 
   - 전일 종가 조회 실패 시에도 포트폴리오 정보는 정상적으로 반환해야 합니다
   - `dayChangeRate`만 `null`로 처리하고 나머지 데이터는 정상 반환합니다

## 확인 필요 사항

백엔드 개발자에게 다음을 확인해달라고 요청하세요:

1. 현재 `/api/stock/portfolio` API의 `holdings` 배열에 `dayChangeRate` 필드가 포함되어 있는지?
2. 전일 종가 데이터를 어디서 가져오는지? (한국투자증권 API, 다른 시세 API 등)
3. 전일 종가 데이터가 없는 경우 어떻게 처리하는지?
4. 변동률 계산 공식이 올바른지?
5. 소수점 첫째 자리까지 반올림하여 반환하는지?

## 수정이 필요한 경우

위 체크리스트에서 하나라도 만족하지 않으면 백엔드 수정이 필요합니다. 특히:

- `dayChangeRate` 필드가 없는 경우 → 반드시 추가 필요
- 전일 종가 데이터를 가져오지 않는 경우 → 데이터 소스 연동 필요
- 계산 공식이 잘못된 경우 → 수정 필요
- 에러 처리 없이 전체 API가 실패하는 경우 → 에러 처리 로직 추가 필요

## 참고: 프론트엔드 구현 내용

프론트엔드에서는 이미 다음을 구현했습니다:

1. `StockHolding` 타입에 `dayChangeRate?: number` 필드 정의
2. `StockHoldingListItem` 컴포넌트에서 `dayChangeRate`를 사용하여 등락률 표시
3. 조건부 렌더링: `dayChangeRate != null`일 때만 표시
4. 색상 적용: 상승(빨간색), 하락(파란색)
5. 포맷팅: 소수점 첫째 자리까지 표시 (예: `+2.5%`, `-1.2%`)

따라서 백엔드에서 `dayChangeRate` 필드만 추가하면 프론트엔드에서 자동으로 표시됩니다.
