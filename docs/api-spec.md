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

### GET `/api/stocks`
전체 종목 목록. 보유 여부와 무관하게 매수 화면의 종목 선택 드롭다운을 채우는 데 사용 (`/api/holdings`는 보유 종목만 반환하므로 신규 계정은 매수할 종목을 고를 수 없었음).

**Response 200**
```json
{
  "success": true,
  "data": [
    { "code": "005930", "name": "삼성전자", "market": "KOSPI", "currentPrice": 73800, "prevClose": 73000, "hasInsight": true }
  ]
}
```
> `hasInsight`: `AI_INSIGHTS`에 해당 종목 인사이트가 하나라도 있는지 여부. 종목 목록 화면은 이 값을 기준으로 인사이트가 있는 종목을 먼저 정렬해 첫 페이지에 노출한다.

### GET `/api/stocks/top-movers?type=FLUCTUATION`
오늘의 랭킹 TOP 10. `type`: `FLUCTUATION`(등락률순, 기본값) / `VOLUME`(거래량순). `TOP_MOVERS` 캐시 테이블을 조회만 하고, KIS 호출은 `MarketRankingBatchService`(장중 10분 간격 + 앱 기동 시 1회)에서만 수행 — AI 인사이트와 동일하게 요청 경로에서 외부 API를 직접 호출하지 않는다.

**Response 200**
```json
{
  "success": true,
  "data": [
    { "code": "443670", "name": "에스피소프트", "market": "KOSDAQ", "currentPrice": 4725, "prevClose": 3634.89 }
  ]
}
```
> 순위에 새로 등장한 종목은 배치가 `STOCKS`에 자동으로 추가한다(find-or-create). `prevClose`는 KIS 순위 API들이 전일종가를 직접 주지 않아 현재가와 등락률(%)로 역산한 값. `type`이 `FLUCTUATION`/`VOLUME` 외의 값이면 `VALIDATION_ERROR`(400).

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

실제 증권사와 동일하게 **주문(접수)과 체결을 구분**한다.
- **시장가**는 현재가로 즉시 체결.
- **지정가**는 접수 시점에 이미 조건(매수: 현재가≤지정가, 매도: 현재가≥지정가)을 만족하면 실제 거래소처럼 즉시 체결되고, 그렇지 않으면 `PENDING`(체결 대기) 상태로 쌓여 시세가 바뀔 때마다(`OrderMatchingBatchService`) 재평가된다.
- **취소는 체결 전(PENDING)에만 가능**하다. 이미 체결된 거래는 `ORDERS`가 append-only라 취소할 수 없다(`docs/db-schema.md` ORDERS/PENDING_ORDERS 절 참고).

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

**Response 201 (즉시 체결된 경우 — 시장가, 또는 조건을 이미 만족한 지정가)**
```json
{
  "success": true,
  "data": {
    "orderId": 45,
    "status": "FILLED",
    "price": 73800,
    "quantity": 10,
    "totalAmount": 738000,
    "cashBalanceAfter": 9262000
  }
}
```

**Response 201 (조건 미충족 지정가 — 체결 대기로 접수)**
```json
{
  "success": true,
  "data": {
    "orderId": 12,
    "status": "PENDING",
    "price": 70000,
    "quantity": 10,
    "totalAmount": 700000,
    "cashBalanceAfter": 9262000
  }
}
```
> `status`가 `FILLED`면 `orderId`는 `ORDERS.id`, `PENDING`이면 `orderId`는 `PENDING_ORDERS.id`를 가리킨다. `price`는 체결가(FILLED) 또는 지정가(PENDING). `PENDING` 상태에서는 현금/보유수량이 아직 반영되지 않으므로 `cashBalanceAfter`는 접수 전과 동일하다.

**에러 케이스**
| 코드 | 상황 | HTTP |
|---|---|---|
| `INSUFFICIENT_BALANCE` | 매수 시 가용 잔고 부족 (다른 대기 중인 매수 주문이 예약한 금액 포함) | 400 |
| `INSUFFICIENT_HOLDING` | 매도 시 가용 보유 수량 초과 (다른 대기 중인 매도 주문이 예약한 수량 포함) | 400 |
| `STOCK_NOT_FOUND` | 존재하지 않는 종목 | 404 |
| `INVALID_ORDER_TYPE` | LIMIT인데 limitPrice 누락 | 400 |

> **동시성 처리**: 이 엔드포인트는 `HOLDINGS.quantity`/`ACCOUNTS.cash_balance`를 갱신하므로, 서비스 레이어에서 비관적 락(`SELECT ... FOR UPDATE`)으로 감싸야 함 — DB 스키마 문서의 동시성 이슈와 동일 지점. `PENDING` 접수는 이 두 값을 직접 바꾸지 않지만, 가용 잔고/수량 계산을 위해 `ACCOUNTS`는 동일하게 잠근다.

---

## 4. 거래 내역

### GET `/api/orders?code=&page=0&size=20`
`code`는 선택 파라미터 (미지정 시 전체 종목). 체결 완료(`ORDERS`)와 체결 대기/취소(`PENDING_ORDERS`)를 시간순으로 합쳐서 내려준다.

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "orderId": 12, "stockCode": "005930", "stockName": "삼성전자", "side": "BUY",
        "quantity": 10, "price": 70000, "orderedAt": "2026-08-27T10:05:00",
        "status": "PENDING", "cancelable": true
      },
      {
        "orderId": 45, "stockCode": "005930", "stockName": "삼성전자", "side": "BUY",
        "quantity": 10, "price": 73800, "orderedAt": "2026-08-27T10:02:00",
        "status": "FILLED", "cancelable": false
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 7
  }
}
```
> `status`는 `PENDING`(체결 대기) / `FILLED`(체결 완료) / `CANCELLED`(체결 전 취소) 중 하나. `cancelable`은 서버가 계산해 내려주며, `PENDING`인 행만 `true`. `FILLED` 행의 `orderId`는 `ORDERS.id`, `PENDING`/`CANCELLED` 행의 `orderId`는 `PENDING_ORDERS.id`다(이미 체결되어 `ORDERS`로 넘어간 `PENDING_ORDERS` 행은 중복 표시를 막기 위해 이 목록에서 제외된다).

### DELETE `/api/orders/{orderId}`
체결 전(PENDING) 주문 취소. `orderId`는 `PENDING_ORDERS.id`를 가리킨다. 이미 체결된 거래는 취소할 수 없다.

**Response 200**
```json
{
  "success": true,
  "data": { "orderId": 12, "cashBalanceAfter": 9262000 }
}
```
> 체결 전이라 현금/보유수량을 원래부터 건드리지 않았으므로, 취소해도 `cashBalanceAfter`는 그대로다.

**에러 케이스**
| 코드 | 상황 | HTTP |
|---|---|---|
| `ORDER_NOT_FOUND` | 존재하지 않거나 본인 계좌 소유가 아닌 주문 | 404 |
| `ORDER_ALREADY_CANCELLED` | 이미 취소된 주문을 다시 취소 시도 | 400 |
| `ORDER_NOT_CANCELABLE` | 이미 체결된(FILLED) 주문을 취소하려는 경우 | 400 |

---

## 5. AI 인사이트 (Spring Boot 배치 캐시 조회)

### GET `/api/insights/today`
대시보드 "오늘의 AI 인사이트" 슬라이드 카드 — 계좌당 **최대 3건**(배열), 보유 종목도 후보에 포함한다 (`docs/db-schema.md` DAILY_PICKS 참고).

**Response 200**
```json
{
  "success": true,
  "data": [
    {
      "stockCode": "005930",
      "stockName": "삼성전자",
      "content": "최근 1주일간 HBM 수요 확대 관련 보도가 이어지며...",
      "sources": [{ "name": "한국경제", "date": "2026-08-26", "title": "삼성전자, HBM4 수율 개선...", "url": "https://..." }],
      "generatedAt": "2026-08-27T08:40:00"
    }
  ]
}
```
**Response 200 (인사이트 없음/조회 실패 시)**
```json
{ "success": true, "data": [] }
```
> AI 인사이트 조회 실패(또는 배치 미실행으로 캐시 없음)가 대시보드 전체를 막지 않도록, 이 엔드포인트는 실패해도 `success: true, data: []`로 응답하고 프론트에서 "최근 뉴스가 없어 인사이트를 불러올 수 없습니다"로 처리 (스토리보드 2.1 에러 처리 참고)

### GET `/api/insights/{code}`
**Response 200**
```json
{
  "success": true,
  "data": {
    "content": "메모리 반도체 업황 개선 기대에도...",
    "sources": [{ "name": "머니투데이", "date": "2026-08-25", "title": "메모리 가격 반등 조짐...", "url": "https://..." }],
    "generatedAt": "2026-08-27T08:00:00"
  }
}
```
> `sources[].title`/`url`은 NewsData.io로 전환하며 추가된 필드 (`archive/rag-service` 백업 이전 기존 응답에는 없었음). 배치가 새로 생성한 인사이트부터 채워지며, 프론트는 두 필드가 없어도(과거 데이터) 정상 표시되도록 옵셔널로 처리한다.

---

## 6. 내부 전용 — AI 인사이트 생성 흐름 (Spring Boot 배치 내부)

별도 네트워크 API가 아니라 `InsightBatchService`(하루 1회 배치, `docs/db-schema.md` 배치 요약 참고)가 직접 호출하는
내부 컴포넌트 체인입니다 (외부 노출 없음). 요청 경로(`/api/insights/*`)에서는 절대 호출하지 않습니다.

1. `NewsDataClient.searchNews(stockName, 10)` — NewsData.io `/api/1/latest`로 종목명 관련 최신 한국어 기사 조회 (최대 10건). 결과가 비어있으면(거래량이 적어 최근 48시간 내 보도가 없는 종목 등) `/api/1/archive`로 최근 2년치를 넓게 훑어 가장 오래된 기사라도 근거로 삼는다 (archive는 NewsData.io 유료 플랜 기능 — 무료 플랜에서는 이 폴백이 조용히 빈 결과로 끝나고 해당 종목은 건너뜀).
2. `InsightGenerationService.generate(stockCode, stockName)` — 상위 5건을 컨텍스트로 구성해 프롬프트 조립
3. `OpenAiClient.chat(systemPrompt, userPrompt)` — OpenAI Chat Completions API 호출, "매수/매도 추천 금지" 시스템 프롬프트 고정
4. 결과(`content` + `sources`)를 `AI_INSIGHTS` 테이블에 INSERT

뉴스가 하나도 없거나(최신+아카이브 모두 실패) LLM 호출이 실패하면 해당 종목은 건너뛰고 다음 종목을 계속 처리합니다
(Spring Boot 배치가 이 흐름을 하루 1회 호출해 캐싱 — 실시간 호출 아님, 비용 절감을 위한 전략, PRD의 성공 기준과 연결).

> **변경 이력**: 과거에는 이 자리에 Spring Boot ↔ Python RAG 서비스(`POST /generate-insight`) 간 내부 HTTP API가
> 있었으나, 오라클 프리티어 메모리 제약으로 Spring Boot 내부 처리로 통합하며 제거했습니다. 옛 계약은
> `archive/rag-service/ARCHIVE.md`에 남아 있습니다.
>
> **뉴스 소스 변경 이력**: 처음엔 네이버 뉴스 검색 API를 썼으나, 네이버가 검색 API 신규 발급을 NCP로 이관하고
> (2026-07-31) 검색 결과의 AI 입력/요약 활용을 약관으로 금지해(2026-09-07 시행) NewsData.io로 교체했습니다.
> NewsData.io는 무료 플랜(200 크레딧/일)으로 개인/상업적 이용을 이용약관에 명시적으로 허용합니다.

---

## 7. 마이페이지 / 관심종목

### GET `/api/users/me`
읽기 전용 프로필 조회

**Response 200**
```json
{
  "success": true,
  "data": {
    "email": "demo@bluemoon.local",
    "name": "Demo",
    "createdAt": "2026-09-10T14:03:59",
    "cashBalance": 10000000,
    "accountCreatedAt": "2026-09-10T14:03:59"
  }
}
```

### GET `/api/watchlist`
내 관심종목 목록. `STOCKS`와 조인한 형태로 `/api/stocks` 응답과 동일한 구조.

**Response 200**
```json
{ "success": true, "data": [{ "code": "035420", "name": "NAVER", "market": "KOSPI", "currentPrice": 208000, "prevClose": 208000 }] }
```

### POST `/api/watchlist/{code}`
관심종목 추가 (이미 있으면 no-op). **Response 201**, `data: null`

### DELETE `/api/watchlist/{code}`
관심종목 제거 (없어도 성공). **Response 200**, `data: null`

---

## 8. 엔드포인트 요약표

| 엔드포인트 | 메서드 | 인증 | 화면 |
|---|---|---|---|
| `/api/auth/signup` | POST | ✕ | 로그인 |
| `/api/auth/login` | POST | ✕ | 로그인 |
| `/api/portfolio/summary` | GET | ✓ | 대시보드 |
| `/api/portfolio/trend` | GET | ✓ | 대시보드 |
| `/api/holdings` | GET | ✓ | 대시보드, 보유종목 |
| `/api/stocks` | GET | ✓ | 매수매도 |
| `/api/stocks/top-movers` | GET | ✓ | 종목목록 |
| `/api/stocks/{code}` | GET | ✓ | 종목상세, 매수매도 |
| `/api/stocks/{code}/price-history` | GET | ✓ | 종목상세 |
| `/api/trades/{code}` | GET | ✓ | 종목상세 |
| `/api/orders` | POST | ✓ | 매수매도 |
| `/api/orders` | GET | ✓ | 거래내역 |
| `/api/orders/{orderId}` | DELETE | ✓ | 거래내역 |
| `/api/insights/today` | GET | ✓ | 대시보드 |
| `/api/insights/{code}` | GET | ✓ | 종목상세 |
| `/api/users/me` | GET | ✓ | 마이페이지 |
| `/api/watchlist` | GET | ✓ | 종목상세 |
| `/api/watchlist/{code}` | POST / DELETE | ✓ | 종목상세 |
