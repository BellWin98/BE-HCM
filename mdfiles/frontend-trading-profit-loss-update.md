# 주문내역 API 응답 변경사항 및 프론트엔드 수정 가이드

## 개요

백엔드 API의 거래 내역 반환 로직이 변경되었습니다. 이전에는 같은 날짜에 매수와 매도가 모두 있는 경우 매수 내역만 반환했지만, 이제는 **매수와 매도 내역을 모두 반환**합니다.

## 백엔드 변경사항

### 변경 전
- 같은 날짜에 매수/매도가 모두 있으면 → **매수 내역만 반환**
- `trades` 배열에 하나의 항목만 포함

### 변경 후
- 같은 날짜에 매수/매도가 모두 있으면 → **매수와 매도 내역 모두 반환**
- `trades` 배열에 각각 별도의 항목으로 포함됨

### 예시

**변경 전 응답:**
```json
{
  "trades": [
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "tradeDate": "2026-02-09",
      "tradeType": "BUY",
      "quantity": 10,
      "price": 70000,
      "amount": 700000,
      "profitLoss": 0,
      "profitLossRate": 0,
      "fee": 5000,
      "tax": 0
    }
  ]
}
```

**변경 후 응답 (같은 날짜에 매수/매도가 모두 있는 경우):**
```json
{
  "trades": [
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "tradeDate": "2026-02-09",
      "tradeType": "BUY",
      "quantity": 10,
      "price": 70000,
      "amount": 700000,
      "profitLoss": 0,
      "profitLossRate": 0,
      "fee": 5000,
      "tax": 0
    },
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "tradeDate": "2026-02-09",
      "tradeType": "SELL",
      "quantity": 5,
      "price": 72000,
      "amount": 360000,
      "profitLoss": 10000,
      "profitLossRate": 2.86,
      "fee": 5000,
      "tax": 3000
    }
  ]
}
```

## 프론트엔드에서 확인/수정해야 할 사항

### 1. 데이터 구조 가정 확인

프론트엔드 코드에서 다음과 같은 가정을 하고 있다면 수정이 필요합니다:

- ❌ **잘못된 가정**: "같은 날짜, 같은 종목에는 하나의 거래만 있다"
- ✅ **올바른 가정**: "같은 날짜, 같은 종목에 매수와 매도가 각각 별도 항목으로 올 수 있다"

### 2. UI 표시 로직 확인

#### 2.1 날짜별 그룹핑

만약 프론트엔드에서 날짜별로 그룹핑하여 표시한다면:

```typescript
// 예시: 날짜별로 그룹핑
const groupedByDate = trades.reduce((acc, trade) => {
  if (!acc[trade.tradeDate]) {
    acc[trade.tradeDate] = [];
  }
  acc[trade.tradeDate].push(trade);
  return acc;
}, {} as Record<string, Trade[]>);
```

**확인사항:**
- 같은 날짜에 여러 항목이 올 수 있으므로 배열로 처리해야 함
- 각 날짜 그룹 내에서 매수/매도 순서를 어떻게 표시할지 결정 필요

#### 2.2 종목별 그룹핑

만약 종목별로 그룹핑한다면:

```typescript
// 예시: 날짜 + 종목별로 그룹핑
const groupedByStockAndDate = trades.reduce((acc, trade) => {
  const key = `${trade.tradeDate}_${trade.stockCode}`;
  if (!acc[key]) {
    acc[key] = [];
  }
  acc[key].push(trade);
  return acc;
}, {} as Record<string, Trade[]>);
```

**확인사항:**
- 같은 날짜, 같은 종목에 매수와 매도가 모두 올 수 있으므로 배열로 처리해야 함

### 3. 정렬 로직 확인

백엔드에서는 날짜순(최신순)으로 정렬하여 반환하지만, 같은 날짜 내에서의 순서는 보장되지 않을 수 있습니다.

**권장사항:**
```typescript
// 같은 날짜 내에서 매수 → 매도 순서로 정렬 (선택사항)
const sortedTrades = trades.sort((a, b) => {
  // 먼저 날짜순 정렬 (최신순)
  const dateCompare = b.tradeDate.localeCompare(a.tradeDate);
  if (dateCompare !== 0) return dateCompare;
  
  // 같은 날짜면 매수(BUY)를 먼저 표시 (선택사항)
  if (a.tradeType === 'BUY' && b.tradeType === 'SELL') return -1;
  if (a.tradeType === 'SELL' && b.tradeType === 'BUY') return 1;
  
  return 0;
});
```

### 4. 필터링 로직 확인

매수만 보기, 매도만 보기 등의 필터가 있다면:

```typescript
// 매수만 필터링
const buyTrades = trades.filter(trade => trade.tradeType === 'BUY');

// 매도만 필터링
const sellTrades = trades.filter(trade => trade.tradeType === 'SELL');
```

**확인사항:**
- 필터링 후에도 같은 날짜에 매수/매도가 모두 있을 수 있음
- 필터링 시 `tradeCount`는 필터링된 결과의 개수로 재계산 필요

### 5. 카운트 로직 확인

`tradeCount`는 이제 매수와 매도를 각각 카운트합니다.

**변경 전:**
- 같은 날짜에 매수/매도가 모두 있으면 → `tradeCount`에 1개만 카운트

**변경 후:**
- 같은 날짜에 매수/매도가 모두 있으면 → `tradeCount`에 2개 카운트

**확인사항:**
- UI에서 표시하는 거래 건수가 증가할 수 있음
- 이는 정상적인 동작입니다 (매수와 매도를 각각 별도 거래로 카운트)

### 6. 매수 항목의 필드 값 확인

매수 항목의 경우 다음 필드가 `0`으로 설정됩니다:

- `profitLoss`: 0 (매수 시 손익 없음)
- `profitLossRate`: 0 (매수 시 손익률 없음)
- `tax`: 0 (매수 시 세금 없음)

**확인사항:**
- 매수 항목에서 손익/손익률/세금을 표시하지 않도록 UI 처리 필요
- 또는 0으로 표시해도 무방

### 7. UI 렌더링 예시

같은 날짜에 매수/매도가 모두 있는 경우:

```tsx
// 예시: 날짜별로 그룹핑하여 표시
{Object.entries(groupedByDate).map(([date, dateTrades]) => (
  <div key={date}>
    <DateHeader date={date} />
    {dateTrades.map((trade, index) => (
      <TradeItem 
        key={`${trade.stockCode}-${trade.tradeType}-${index}`}
        trade={trade}
      />
    ))}
  </div>
))}
```

**주의사항:**
- `key` prop에 `index`를 포함하거나, 고유한 식별자를 사용해야 함
- 같은 날짜, 같은 종목에 매수/매도가 모두 올 수 있으므로 `stockCode`만으로는 고유하지 않을 수 있음

### 8. 테스트 케이스

다음 시나리오를 테스트해야 합니다:

1. **같은 날짜에 매수만 있는 경우**
   - 정상적으로 표시되는지 확인

2. **같은 날짜에 매도만 있는 경우**
   - 정상적으로 표시되는지 확인

3. **같은 날짜에 매수와 매도가 모두 있는 경우** ⭐ **중요**
   - 둘 다 표시되는지 확인
   - 날짜 그룹핑이 올바르게 동작하는지 확인
   - 정렬이 올바른지 확인

4. **같은 날짜, 같은 종목에 매수와 매도가 모두 있는 경우** ⭐ **중요**
   - 둘 다 별도 항목으로 표시되는지 확인
   - UI에서 구분이 명확한지 확인

## 체크리스트

프론트엔드 개발자가 확인해야 할 사항:

- [ ] 같은 날짜에 매수/매도가 모두 올 수 있다는 것을 가정하고 코드가 작성되어 있는가?
- [ ] 날짜별 그룹핑 로직이 배열을 올바르게 처리하는가?
- [ ] 종목별 그룹핑 로직이 배열을 올바르게 처리하는가?
- [ ] 정렬 로직이 같은 날짜 내에서도 올바르게 동작하는가?
- [ ] 필터링 로직이 변경된 데이터 구조에서도 올바르게 동작하는가?
- [ ] `tradeCount`가 증가하는 것에 대해 UI가 적절히 대응하는가?
- [ ] 매수 항목의 `profitLoss`, `profitLossRate`, `tax`가 0인 경우를 올바르게 처리하는가?
- [ ] React의 `key` prop이 고유한 값으로 설정되어 있는가?
- [ ] 같은 날짜, 같은 종목에 매수/매도가 모두 있는 경우를 테스트했는가?

## API 응답 구조 (변경 없음)

API 응답 구조 자체는 변경되지 않았으며, 단지 `trades` 배열에 더 많은 항목이 포함될 수 있습니다.

```typescript
interface TradingProfitLossResponse {
  period: string;
  totalBuyAmount: number;
  totalSellAmount: number;
  totalProfitLoss: number;
  totalProfitLossRate: number;
  totalFee: number;
  totalTax: number;
  tradeCount: number; // 이제 매수/매도를 각각 카운트
  trades: Trade[]; // 같은 날짜에 매수/매도가 모두 있으면 각각 별도 항목으로 포함
}

interface Trade {
  stockCode: string;
  stockName: string;
  tradeDate: string; // YYYY-MM-DD 형식
  tradeType: "BUY" | "SELL";
  quantity: number;
  price: number;
  amount: number;
  profitLoss: number; // 매수 시 0
  profitLossRate: number; // 매수 시 0
  fee: number;
  tax: number; // 매수 시 0
}
```

## 문의사항

프론트엔드 수정 중 문제가 발생하거나 추가 확인이 필요한 사항이 있으면 백엔드 팀에 문의하세요.
