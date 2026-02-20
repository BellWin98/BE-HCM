# 주문내역 기능 백엔드 구현 요구사항

## 개요
프론트엔드에서 주문내역(거래 내역) 기능을 구현했습니다. 백엔드 API가 다음 요구사항을 충족하는지 확인하고, 필요시 수정이 필요합니다.

## API 엔드포인트

### POST `/api/stock/trading-profit-loss`

**요청 본문 (Request Body):**
```json
{
  "startDate": "2020-01-01",
  "endDate": "2026-02-20",
  "periodType": "ALL"
}
```

**요청 필드:**
- `startDate` (string, required): 시작 날짜 (YYYY-MM-DD 형식)
- `endDate` (string, required): 종료 날짜 (YYYY-MM-DD 형식)
- `periodType` (string, required): 기간 타입 ("ALL", "DAILY", "WEEKLY", "MONTHLY", "YEARLY")

**응답 본문 (Response Body):**
```json
{
  "data": {
    "period": "전체 기간",
    "totalBuyAmount": 10000000,
    "totalSellAmount": 5000000,
    "totalProfitLoss": 2000000,
    "totalProfitLossRate": 20.5,
    "totalFee": 50000,
    "totalTax": 30000,
    "tradeCount": 10,
    "trades": [
      {
        "stockCode": "005930",
        "stockName": "삼성전자",
        "tradeDate": "2026-02-09",
        "tradeType": "BUY",
        "quantity": 5,
        "price": 24995,
        "amount": 124975,
        "profitLoss": 0,
        "profitLossRate": 0,
        "fee": 5000,
        "tax": 0
      },
      {
        "stockCode": "005930",
        "stockName": "삼성전자",
        "tradeDate": "2026-02-09",
        "tradeType": "BUY",
        "quantity": 5,
        "price": 25730,
        "amount": 128650,
        "profitLoss": 0,
        "profitLossRate": 0,
        "fee": 5000,
        "tax": 0
      },
      {
        "stockCode": "000660",
        "stockName": "SK하이닉스",
        "tradeDate": "2026-02-08",
        "tradeType": "SELL",
        "quantity": 4,
        "price": 294060,
        "amount": 1176240,
        "profitLoss": 50000,
        "profitLossRate": 4.5,
        "fee": 5000,
        "tax": 3000
      }
    ]
  }
}
```

## 필수 응답 필드 상세

### TradingProfitLossSummary (최상위 객체)
- `period` (string): 기간 설명 문자열 (예: "전체 기간", "2026년 2월 실현수익")
- `totalBuyAmount` (number): 총 매수 금액
- `totalSellAmount` (number): 총 매도 금액
- `totalProfitLoss` (number): 총 손익
- `totalProfitLossRate` (number): 총 손익률 (%)
- `totalFee` (number): 총 수수료
- `totalTax` (number): 총 세금
- `tradeCount` (number): 거래 건수
- `trades` (array): 거래 내역 배열 (중요!)

### TradingProfitLoss (trades 배열의 각 항목)
- `stockCode` (string, required): 종목 코드 (예: "005930")
- `stockName` (string, required): 종목명 (예: "삼성전자")
- `tradeDate` (string, required): 거래일자 (YYYY-MM-DD 형식, 예: "2026-02-09")
- `tradeType` (string, required): 거래 유형 ("BUY" 또는 "SELL")
- `quantity` (number, required): 거래 수량 (주 수)
- `price` (number, required): 주당 가격 (원)
- `amount` (number, required): 거래 금액 (quantity * price)
- `profitLoss` (number): 손익 (매도 시에만 의미 있음, 매수 시 0 가능)
- `profitLossRate` (number): 손익률 (%)
- `fee` (number): 수수료
- `tax` (number): 세금 (매도 시 발생)

## 중요 체크리스트

### 1. 거래 내역 포함 여부
- [ ] `trades` 배열이 반드시 포함되어야 합니다
- [ ] `periodType`이 "ALL"일 때, 모든 기간의 거래 내역을 반환해야 합니다
- [ ] 매수(BUY)와 매도(SELL) 거래 모두 포함되어야 합니다

### 2. 날짜 형식
- [ ] `tradeDate`는 반드시 "YYYY-MM-DD" 형식이어야 합니다 (예: "2026-02-09")
- [ ] 날짜는 ISO 8601 표준을 따르거나, 적어도 "YYYY-MM-DD" 형식이어야 합니다

### 3. 거래 유형
- [ ] `tradeType`은 반드시 "BUY" 또는 "SELL" 중 하나여야 합니다 (대소문자 구분)
- [ ] 매수 거래는 "BUY", 매도 거래는 "SELL"로 표시되어야 합니다

### 4. 데이터 정렬
- [ ] `trades` 배열은 날짜순으로 정렬되어야 합니다 (최신순 또는 오래된 순, 프론트엔드에서 재정렬 가능)
- [ ] 같은 날짜의 거래는 시간순으로 정렬하는 것이 좋습니다

### 5. 빈 상태 처리
- [ ] 거래 내역이 없을 경우 `trades`는 빈 배열 `[]`로 반환해야 합니다
- [ ] `tradeCount`는 0이어야 합니다

### 6. 데이터 정확성
- [ ] `stockName`은 한글 종목명이어야 합니다
- [ ] `price`는 주당 가격(원 단위)이어야 합니다
- [ ] `quantity`는 거래한 주 수(정수)여야 합니다
- [ ] `amount`는 `quantity * price`와 일치해야 합니다

## 프론트엔드 사용 예시

프론트엔드에서는 다음과 같이 사용합니다:

```typescript
// API 호출
const data = await api.getTradingProfitLoss({
  startDate: '2020-01-01',
  endDate: formatDateLocal(today),
  periodType: 'ALL',
}) as TradingProfitLossSummary;

// 주문내역 다이얼로그에 전달
<StockTradingHistoryDialog
  trades={data.trades}
  // ...
/>
```

## UI 표시 요구사항

프론트엔드에서 다음과 같이 표시됩니다:

1. **날짜**: 왼쪽에 "M.d" 형식으로 표시 (예: "2.9")
2. **종목명**: 큰 폰트로 진한 회색 표시
3. **거래 정보**:
    - 구매: 회색 텍스트로 "N주 구매 완료"
    - 판매: 파란색 텍스트로 "N주 판매 완료"
4. **주당 가격**: 오른쪽에 "주당 N원" 형식으로 표시

## 테스트 케이스

다음 시나리오를 테스트해야 합니다:

1. **정상 케이스**: 거래 내역이 있는 경우
    - 매수와 매도 거래가 모두 포함되어야 함
    - 날짜 형식이 올바른지 확인
    - 모든 필수 필드가 포함되어 있는지 확인

2. **빈 상태**: 거래 내역이 없는 경우
    - `trades`가 빈 배열 `[]`로 반환되어야 함
    - `tradeCount`가 0이어야 함

3. **기간 필터링**: `periodType`이 "ALL"이 아닌 경우
    - 해당 기간의 거래만 반환되어야 함
    - 하지만 주문내역 기능에서는 "ALL"을 사용하므로 모든 거래가 필요함

4. **날짜 범위**: `startDate`와 `endDate` 범위 내의 거래만 반환
    - 주문내역에서는 넓은 범위(2020-01-01 ~ 현재)를 사용

## 추가 고려사항

1. **성능**: 거래 내역이 많을 경우를 고려하여 페이징이나 제한을 고려할 수 있지만, 현재는 전체 내역이 필요합니다.

2. **인증**: API는 인증된 사용자만 접근 가능해야 하며, 해당 사용자의 거래 내역만 반환해야 합니다.

3. **에러 처리**:
    - 잘못된 날짜 형식일 경우 적절한 에러 메시지 반환
    - 데이터베이스 오류 시 적절한 에러 응답

## 확인 필요 사항

백엔드 개발자에게 다음을 확인해달라고 요청하세요:

1. 현재 `/api/stock/trading-profit-loss` API가 위의 응답 구조를 정확히 따르는지?
2. `trades` 배열이 항상 포함되는지?
3. `tradeDate`가 "YYYY-MM-DD" 형식인지?
4. `tradeType`이 "BUY"/"SELL"로 정확히 반환되는지?
5. 매수와 매도 거래가 모두 포함되는지?
6. 거래 내역이 없을 때 빈 배열을 반환하는지?

## 수정이 필요한 경우

위 체크리스트에서 하나라도 만족하지 않으면 백엔드 수정이 필요합니다. 특히:

- `trades` 배열이 누락된 경우 → 반드시 추가 필요
- 날짜 형식이 다른 경우 → "YYYY-MM-DD" 형식으로 수정 필요
- `tradeType` 값이 다른 경우 → "BUY"/"SELL"로 수정 필요
- 매수 또는 매도만 반환하는 경우 → 둘 다 반환하도록 수정 필요
