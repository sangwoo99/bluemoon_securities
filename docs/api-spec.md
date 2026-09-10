# 블루문 — API 명세서

- Base URL(로컬): `http://localhost:8080`
- 인증: `Authorization: Bearer {accessToken}` (로그인/회원가입 제외 전 엔드포인트 필수)
- 공통 응답 포맷:
```json
{
  "success": true,
  "data": { },
  "error": null
}
```
- 공통 에러 포맷:
```json
{
  "success": false,
  "data": null,
  "error": { "code": "INSUFFICIENT_BALANCE", "message": "잔고가 부족합니다." }
}
```

---

## 0. 인증

### POST `/api/auth/signup`
회원가입

**Request Body**
```json
{ "email": "user@example.com", "password": "********", "name": "홍길동" }
```

**Response 201**
```json
{ "success": true, "data": { "userId": 1, "accountId": 1 } }
```
> 가입과 동시에 `ACCOUNTS`에 계좌 1건 생성, `cash_balance`는 시드머니(10,000,000원)로 초기화

### POST `/api/auth/login`
**Request Body**
```json
{ "email": "user@example.com", "password": "********" }
```

**Response 200**
```json
{ "success": true, "data": { "accessToken": "...", "refreshToken": "..." } }
```

---

## 1. 대시보드

### GET `/api/portfolio/summary`
**Response 200**
```json
{
  "success": true,
  "data": {
    "totalValue": 10640000,
    "totalCost": 10415000,
    "totalGain": 225000,
    "totalGainRate": 2.16,
    "todayChange": 48300,
    "todayChangeRate": 0.46,
    "holdingCount": 5
  }
}
```
> `totalValue`, `totalCost` 등은 `HOLDINGS × STOCKS 현재가`로 서버에서 계산해 반환 (프론트에서 계산하지 않음 — 금액 계산 로직은 백엔드 단일 지점에서만 수행)

### GET `/api/portfolio/trend?period=1M`
`period`: `1W` / `1M` / `3M` / `1Y`

**Response 200**
```json
{
  "success": true,
  "data": [
    { "date": "2026-08-14", "value": 10190000 },
    { "date": "2026-08-27", "value": 10640000 }
  ]
}
```
> `ACCOUNT_SNAPSHOTS` 테이블 조회

---

## 2. 보유 종목 / 종목 상세

### GET `/api/holdings`
**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "quantity": 50,
      "avgPrice": 71200,
      "currentPrice": 73800,
      "evalValue": 3690000,
      "evalGain": 130000,
      "evalGainRate": 3.65
    }
  ]
}
```

### GET `/api/stocks/{code}`
**Response 200**
```json
{
  "success": true,
  "data": {
    "code": "005930",
    "name": "삼성전자",
    "market": "KOSPI",
    "currentPrice": 73800,
    "prevClose": 73000
  }
}
```
**Response 404** — 존재하지 않는 종목 코드

### GET `/api/stocks/{code}/price-history?days=30`
**Response 200**
```json
{
  "success": true,
  "data": [
    { "date": "2026-07-29", "price": 70200 },
    { "date": "2026-08-27", "price": 73800 }
  ]
}
```
> `PRICE_SNAPSHOTS` 조회. 요청한 `days`만큼 데이터가 없으면(신규 상장 등) 존재하는 만큼만 반환 (에러 아님)

### GET `/api/trades/{code}`
특정 종목의 내 매매 이력 (종목상세 하단 표)

**Response 200**
```json
{
  "success": true,
  "data": [
    { "orderId": 12, "side": "BUY", "quantity": 30, "price": 69800, "orderedAt": "2026-07-02T09:31:00" }
  ]
}
```

---

## 3. 매수 / 매도

### POST `/api/orders`
**Request Body**
```json
{
  "stockCode": "005930",
  "side": "BUY",
  "orderType": "MARKET",
  "quantity": 10,
  "limitPrice": null
}
```
> `orderType`이 `LIMIT`이면 `limitPrice` 필수

**Response 201**
```json
{
  "success": true,
  "data": {
    "orderId": 45,
    "filledPrice": 73800,
    "filledQuantity": 10,
    "totalAmount": 738000,
    "cashBalanceAfter": 9262000
  }
}
```

**에러 케이스**
| 코드 | 상황 | HTTP |
|---|---|---|
| `INSUFFICIENT_BALANCE` | 매수 시 현금 잔고 부족 | 400 |
| `INSUFFICIENT_HOLDING` | 매도 시 보유 수량 초과 | 400 |
| `STOCK_NOT_FOUND` | 존재하지 않는 종목 | 404 |
| `INVALID_ORDER_TYPE` | LIMIT인데 limitPrice 누락 | 400 |

> **동시성 처리**: 이 엔드포인트는 `HOLDINGS.quantity`/`ACCOUNTS.cash_balance`를 갱신하므로, 서비스 레이어에서 비관적 락(`SELECT ... FOR UPDATE`) 또는 낙관적 락(버전 컬럼)으로 감싸야 함 — DB 스키마 문서의 동시성 이슈와 동일 지점

---

## 4. 거래 내역

### GET `/api/orders?code=&page=0&size=20`
`code`는 선택 파라미터 (미지정 시 전체 종목)

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      { "orderId": 45, "stockCode": "005930", "stockName": "삼성전자", "side": "BUY", "quantity": 10, "price": 73800, "orderedAt": "2026-08-27T10:02:00" }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 7
  }
}
```

---

## 5. AI 인사이트 (Python RAG 서비스 프록시)

### GET `/api/insights/today`
**Response 200**
```json
{
  "success": true,
  "data": {
    "stockCode": "005930",
    "stockName": "삼성전자",
    "content": "최근 1주일간 HBM 수요 확대 관련 보도가 이어지며...",
    "sources": [{ "name": "한국경제", "date": "2026-08-26" }],
    "generatedAt": "2026-08-27T08:40:00"
  }
}
```
**Response 200 (인사이트 없음/RAG 서비스 장애 시)**
```json
{ "success": true, "data": null }
```
> RAG 서비스 장애가 대시보드 전체를 막지 않도록, 이 엔드포인트는 실패해도 `success: true, data: null`로 응답하고 프론트에서 "일시적으로 인사이트를 불러올 수 없습니다"로 처리 (스토리보드 2.1 에러 처리 참고)

### GET `/api/insights/{code}`
**Response 200**
```json
{
  "success": true,
  "data": {
    "content": "메모리 반도체 업황 개선 기대에도...",
    "sources": [{ "name": "머니투데이", "date": "2026-08-25" }],
    "generatedAt": "2026-08-27T08:00:00"
  }
}
```

---

## 6. 내부 전용 — Spring Boot ↔ Python RAG 서비스

Spring Boot가 Python RAG 서비스를 호출하는 내부 API (외부에 노출되지 않음, 같은 VM 내부 통신)

### POST `{rag-service}/generate-insight`
**Request Body**
```json
{ "stockCode": "005930", "stockName": "삼성전자" }
```
**Response 200**
```json
{ "content": "...", "sources": [{ "name": "한국경제", "date": "2026-08-26" }] }
```
> Spring Boot 배치가 이 엔드포인트를 하루 1회 호출해 `AI_INSIGHTS` 테이블에 저장 (실시간 호출 아님 — 비용 절감을 위한 캐싱 전략, PRD의 성공 기준과 연결)

---

## 7. 엔드포인트 요약표

| 엔드포인트 | 메서드 | 인증 | 화면 |
|---|---|---|---|
| `/api/auth/signup` | POST | ✕ | 로그인 |
| `/api/auth/login` | POST | ✕ | 로그인 |
| `/api/portfolio/summary` | GET | ✓ | 대시보드 |
| `/api/portfolio/trend` | GET | ✓ | 대시보드 |
| `/api/holdings` | GET | ✓ | 대시보드, 보유종목 |
| `/api/stocks/{code}` | GET | ✓ | 종목상세, 매수매도 |
| `/api/stocks/{code}/price-history` | GET | ✓ | 종목상세 |
| `/api/trades/{code}` | GET | ✓ | 종목상세 |
| `/api/orders` | POST | ✓ | 매수매도 |
| `/api/orders` | GET | ✓ | 거래내역 |
| `/api/insights/today` | GET | ✓ | 대시보드 |
| `/api/insights/{code}` | GET | ✓ | 종목상세 |
