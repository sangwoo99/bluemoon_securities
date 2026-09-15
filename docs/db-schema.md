# 블루문 — DB 스키마 설계 문서 (MyBatis + Oracle 버전)

> **업데이트 안내**: 채용 시장 분석 결과, 전통 금융권(은행/증권사 계정계) 채용 수요가 신생 핀테크보다 많다고 판단하여 스택을 전환했습니다.
> - ~~Spring Data JPA + QueryDSL~~ → **MyBatis** (XML 매퍼 기반, SQL 직접 제어)
> - ~~PostgreSQL~~ → **Oracle Database** (로컬 개발: `gvenzl/oracle-free` Docker 이미지 / 배포: Oracle Cloud Autonomous Database Always Free)
> - 언어 버전은 Spring Boot 3.x의 최소 요구사항에 맞춰 **Java 17**을 사용합니다(`docs/PRD.md` PART 2 참고).
>
> 테이블 구성·컬럼·동시성 처리 지점은 기존 설계를 그대로 유지하고, 타입만 Oracle 데이터 타입으로 재작성했습니다.

이 문서는 ERD의 각 테이블을 상세화한 것으로, MyBatis 매퍼/DDL 작성의 기준이 됩니다.

---

## ERD 개요

```
USERS 1───1 ACCOUNTS 1───N HOLDINGS N───1 STOCKS
                │                              │
                ├──N ORDERS N──────────────────┤
                ├──N PENDING_ORDERS N───────────┤
                │                              │
                ├──N ACCOUNT_SNAPSHOTS         ├──N PRICE_SNAPSHOTS
                │                              │
                ├──N WATCHLISTS N──────────────┤
                │                              │
                └──N DAILY_PICKS N───1 AI_INSIGHTS N───1 STOCKS

STOCKS 1───N TOP_MOVERS
```

## 1. 테이블 목록 및 역할

| 테이블 | 역할 | 관련 화면 |
|---|---|---|
| `USERS` | 사용자 계정 | 로그인 |
| `ACCOUNTS` | 모의투자 계좌 (현금 잔고 포함) | 전체 |
| `STOCKS` | 종목 마스터 (기준정보) | 전체 |
| `HOLDINGS` | 보유 종목 (수량·평단가) | 대시보드, 보유종목 |
| `ORDERS` | 체결이 실제로 일어난 거래 원장(append-only) | 매수매도, 거래내역, 종목상세 |
| `PENDING_ORDERS` | 체결 전 대기 중인 지정가 주문 티켓 | 매수매도, 거래내역 |
| `PRICE_SNAPSHOTS` | 종목 시세 일별 스냅샷 | 종목상세 (시세 추이) |
| `ACCOUNT_SNAPSHOTS` | 계좌 총자산 일별 스냅샷 | 대시보드 (자산 변화 추이) |
| `AI_INSIGHTS` | AI 인사이트 캐시 (배치 생성) | 대시보드, 종목상세 |
| `DAILY_PICKS` | 계좌별 "오늘의 추천 종목" 매핑 | 대시보드 |
| `WATCHLISTS` | 계좌별 관심종목(즐겨찾기) | 종목상세 |
| `TOP_MOVERS` | "오늘의 상승률 TOP 10" 캐시 | 종목목록 |

---

## 2. 테이블 상세 (Oracle DDL 기준)

### USERS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, GENERATED ALWAYS AS IDENTITY | Oracle 12c+ IDENTITY 컬럼 사용 (시퀀스+트리거 대신) |
| email | VARCHAR2(255) | UNIQUE, NOT NULL | 로그인 ID |
| password_hash | VARCHAR2(255) | NOT NULL | BCrypt 해시 |
| name | VARCHAR2(100) | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |

### ACCOUNTS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| user_id | NUMBER(19) | FK → USERS.id, UNIQUE (1:1), NOT NULL | |
| cash_balance | NUMBER(18,2) | NOT NULL | 매수 가능 현금. 가입 시 10,000,000 지급 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |

> `cash_balance` 갱신(`POST /api/orders`)은 반드시 `SELECT ... FOR UPDATE`로 잠근 트랜잭션 내에서 수행. 낙관적 락(`version` 컬럼)은 두지 않고 비관적 락 하나로 단순화(CLAUDE.md 절대 규칙 — 두 락 전략을 동시에 쓰지 않는다).

### STOCKS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| code | VARCHAR2(6) | PK | 종목코드 (예: 005930) |
| name | VARCHAR2(100) | NOT NULL | |
| market | VARCHAR2(10) | NOT NULL | KOSPI / KOSDAQ |
| current_price | NUMBER(18,2) | NOT NULL | |
| prev_close | NUMBER(18,2) | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

> `current_price`는 한국투자증권 Open API 폴링 배치로 주기 갱신 예정(현재는 V2 마이그레이션 시드값 고정). 실시간 스트리밍 아님 (PRD Out-of-scope).

### HOLDINGS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| account_id | NUMBER(19) | FK → ACCOUNTS.id, NOT NULL | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| quantity | NUMBER(19) | NOT NULL, DEFAULT 0, CHECK (quantity >= 0) | 보유 수량 |
| avg_price | NUMBER(18,2) | NOT NULL, DEFAULT 0 | 평단가 (이동평균법) |
| updated_at | TIMESTAMP | NOT NULL | |

> **제약**: `UNIQUE(account_id, stock_code)`. 수량이 0이 되어도 행은 삭제하지 않고 유지(재매수 시 평단가 이력 참고 목적) — 조회 시 `quantity > 0`인 행만 필터링.
> **동시성**: 매수/매도 시 이 행을 `SELECT ... FOR UPDATE`(Oracle 비관적 락 문법)로 잠근다. 락 획득 순서는 항상 `ACCOUNTS → HOLDINGS`로 고정해 데드락 방지 (CLAUDE.md 절대 규칙).

### ORDERS (append-only)
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| account_id | NUMBER(19) | FK → ACCOUNTS.id, NOT NULL | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| side | VARCHAR2(4) | NOT NULL | `BUY` / `SELL` |
| order_type | VARCHAR2(6) | NOT NULL | `MARKET` / `LIMIT` |
| quantity | NUMBER(19) | NOT NULL, CHECK (quantity > 0) | |
| limit_price | NUMBER(18,2) | NULL (LIMIT일 때만) | |
| filled_price | NUMBER(18,2) | NOT NULL | 체결가 |
| filled_quantity | NUMBER(19) | NOT NULL | |
| total_amount | NUMBER(18,2) | NOT NULL | |
| status | VARCHAR2(10) | NOT NULL | 항상 `FILLED` (체결된 거래만 기록) |
| cancel_of_order_id | NUMBER(19) | NULL, FK → ORDERS.id | 레거시 컬럼. 현재 로직은 채우지 않음(아래 설계 변경 참고) |
| ordered_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |

> **설계 의도**: append-only. UPDATE/DELETE 금지. 체결이 실제로 일어난 거래만 이 테이블에 들어온다 — 주문 접수 자체는 `PENDING_ORDERS`가 담당한다.
> **인덱스**: `(account_id, ordered_at DESC)`, `(account_id, stock_code, ordered_at DESC)` — 거래내역/종목별 이력 조회용.
> **설계 변경 이력**: 초기 버전은 모든 주문(시장가·지정가 불문)이 즉시 체결되는 단순 모델이라 "취소"를 원주문과 반대 방향의 체결로 흉내 냈고, 그 반대매매 레코드가 원주문을 `cancel_of_order_id`로 참조했다. 이후 실제 증권사처럼 주문(접수)과 체결을 분리하면서 이 흉내-취소 로직은 제거했고(시장가 주문은 취소할 이유 자체가 없고, 지정가 주문은 체결 전에만 취소 가능), `cancel_of_order_id`는 과거 데이터 호환을 위해 컬럼만 남겨두었다. 이제 취소는 `PENDING_ORDERS` 절 참고.

### PENDING_ORDERS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| account_id | NUMBER(19) | FK → ACCOUNTS.id, NOT NULL | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| side | VARCHAR2(4) | NOT NULL | `BUY` / `SELL` |
| quantity | NUMBER(19) | NOT NULL, CHECK (quantity > 0) | |
| limit_price | NUMBER(18,2) | NOT NULL | 지정가 |
| status | VARCHAR2(10) | NOT NULL | `PENDING` / `FILLED` / `CANCELLED` |
| filled_order_id | NUMBER(19) | NULL, FK → ORDERS.id | 체결되면 그때 생성된 ORDERS 행을 가리킴 |
| ordered_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |
| resolved_at | TIMESTAMP | NULL | FILLED/CANCELLED로 전이된 시각 |

> **ORDERS와의 차이**: 지정가 주문의 "접수" 자체는 아직 실제 거래가 아니므로 append-only일 필요가 없다 — 이 행 자체를 `PENDING → FILLED`/`PENDING → CANCELLED`로 UPDATE한다(append-only 규칙은 체결이 실제로 일어난 거래를 기록하는 `ORDERS`에만 적용, CLAUDE.md 절대 규칙).
> **시장가 주문은 이 테이블을 거치지 않는다.** 현재가로 그 자리에서 즉시 체결되어 곧바로 `ORDERS`에 기록된다. 지정가 주문도 접수 시점에 이미 조건(매수: 현재가≤지정가, 매도: 현재가≥지정가)을 만족하면 실제 거래소처럼 즉시 체결되고, 그렇지 않을 때만 이 테이블에 `PENDING`으로 쌓인다.
> **체결 조건 재평가**: 별도 스케줄을 두지 않고, 시세가 바뀌는 유일한 시점인 `PriceUpdateBatchService`(10분 간격 시세 갱신) 직후 `OrderMatchingBatchService`가 전체 `PENDING` 행을 조건과 대조해 체결시킨다. 체결가는 그 사이 더 유리해진 현재가가 아니라 항상 주문 당시의 지정가로 고정된다.
> **가용 잔고/수량**: 매수 대기 주문은 `SUM(quantity * limit_price)`만큼 현금을, 매도 대기 주문은 `SUM(quantity)`만큼 보유수량을 예약한 것으로 계산해 신규 주문 접수 시 검증한다(`OrderService.placeLimitOrder`) — 체결 전까지 `ACCOUNTS.cash_balance`/`HOLDINGS.quantity` 자체는 건드리지 않는다.
> **취소는 PENDING 상태에서만 가능**: 이미 체결(FILLED)된 건 `ORDERS`가 append-only라 취소할 수 없다. 취소는 이 행을 `CANCELLED`로 바꾸는 것뿐이라 현금/보유수량 원상복구가 필요 없다(원래 반영된 적이 없으므로).
> **락 순서**: 체결 처리 시 `ACCOUNTS → HOLDINGS → PENDING_ORDERS` 순으로 잠근다(CLAUDE.md 절대 규칙의 확장). 취소는 이 행 자체만 `SELECT ... FOR UPDATE`로 잠가 매칭 배치와의 경합을 막는다.

### PRICE_SNAPSHOTS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| price | NUMBER(18,2) | NOT NULL | |
| snapshot_date | DATE | NOT NULL | |

> **제약**: `UNIQUE(stock_code, snapshot_date)`. 장마감 후 일 1회 배치로 적재. `GET /api/stocks/{code}/price-history` 조회 대상.

### ACCOUNT_SNAPSHOTS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| account_id | NUMBER(19) | FK → ACCOUNTS.id, NOT NULL | |
| total_value | NUMBER(18,2) | NOT NULL | |
| snapshot_date | DATE | NOT NULL | |

> **제약**: `UNIQUE(account_id, snapshot_date)`. 일 1회 배치로 `HOLDINGS × STOCKS.current_price` 합산 값을 스냅샷. `GET /api/portfolio/trend` 조회 대상.

### AI_INSIGHTS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| content | CLOB | NOT NULL | AI 생성 요약 텍스트 |
| sources | CLOB | NOT NULL, CHECK (sources IS JSON) | `[{"name": "...", "date": "...", "title": "...", "url": "..."}]`. 네이티브 JSON 타입(Oracle 21c+) 대신 이식성이 높은 CLOB+JSON 제약을 사용. `title`/`url`은 NewsData.io 전환 후 추가된 필드라 그 이전 행에는 없음(프론트는 옵셔널로 처리) |
| generated_at | TIMESTAMP | NOT NULL | |

> **오직 배치(하루 1회)만 이 테이블에 INSERT**. Spring Boot 스케줄러(`InsightBatchService`)가 `NewsDataClient`(뉴스 검색)+`OpenAiClient`(요약 생성)를 직접 호출해 결과를 저장 (과거엔 Python RAG 서비스의 `POST /generate-insight`를 호출했으나 오라클 프리티어 메모리 제약으로 Spring Boot 내부 처리로 통합, `archive/rag-service` 참고). 요청 경로(`/api/insights/*`)는 항상 이 테이블을 SELECT만 함 (CLAUDE.md 절대 규칙).
> **인덱스**: `(stock_code, generated_at DESC)` — 종목별 최신 인사이트 조회.

### DAILY_PICKS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| account_id | NUMBER(19) | FK → ACCOUNTS.id, NOT NULL | |
| insight_id | NUMBER(19) | FK → AI_INSIGHTS.id, NOT NULL | |
| pick_date | DATE | NOT NULL | |

> **제약**: `UNIQUE(account_id, pick_date, insight_id)` (V8 마이그레이션 — 이전엔 `UNIQUE(account_id, pick_date)`로 계좌당 하루 1건뿐이었음). "오늘의 AI 인사이트"(`GET /api/insights/today`, 대시보드 슬라이드 카드)는 계좌당 하루 최대 3건 — 인사이트가 있는 종목(보유 종목 포함) 중 거래량 TOP 랭킹(`TOP_MOVERS`, `RankType.VOLUME`) 순으로 훑어 배치가 선정해 이 테이블에 기록. `AI_INSIGHTS`를 JOIN해 응답.

### WATCHLISTS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| account_id | NUMBER(19) | FK → ACCOUNTS.id, NOT NULL | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| created_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |

> **제약**: `UNIQUE(account_id, stock_code)`. 종목상세 화면의 ★ 토글로 추가/삭제. 조회 시 `STOCKS`와 배치 조회 후 애플리케이션에서 합치는 방식(`WatchlistService.getMyWatchlist`, `OrderService`의 기존 배치 패턴과 동일 — N+1 방지).

### TOP_MOVERS
| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | NUMBER(19) | PK, IDENTITY | |
| stock_code | VARCHAR2(6) | FK → STOCKS.code, NOT NULL | |
| rank_type | VARCHAR2(12) | NOT NULL | `FLUCTUATION`(등락률순) / `VOLUME`(거래량순) |
| rank_no | NUMBER(3) | NOT NULL | 1~10 |
| change_rate | NUMBER(6,2) | NOT NULL | 전일대비 등락률(%) — 두 랭킹 타입 모두 채움 |
| volume | NUMBER(19) | NULL | 누적거래량. `rank_type=VOLUME`일 때만 값 존재 |
| captured_at | TIMESTAMP | NOT NULL, DEFAULT SYSTIMESTAMP | |

> **다른 테이블과 성격이 다름**: `ORDERS`/`WATCHLISTS`처럼 사용자 데이터를 쌓는 게 아니라 "지금 이 순간의 랭킹"을 보여주는 캐시라서, `MarketRankingBatchService`가 실행될 때마다 `rank_type`별로 해당 타입 행만 `DELETE` 후 다시 `INSERT`한다(append-only 규칙은 `ORDERS`에만 적용, 두 랭킹은 서로 독립 갱신). KIS 등락률 순위(`FHPST01700000`)/거래량 순위(`FHPST01710000`)를 코스피·코스닥 각각 조회해 타입별 상위 10개를 저장하며, 순위에 새로 등장한 종목은 `STOCKS`에 find-or-create로 추가한다.

---

## 3. 관계 요약

- `USERS 1 : 1 ACCOUNTS`
- `ACCOUNTS 1 : N HOLDINGS / ORDERS / PENDING_ORDERS / ACCOUNT_SNAPSHOTS / DAILY_PICKS / WATCHLISTS`
- `STOCKS 1 : N HOLDINGS / ORDERS / PENDING_ORDERS / PRICE_SNAPSHOTS / AI_INSIGHTS / WATCHLISTS / TOP_MOVERS`
- `PENDING_ORDERS 0..1 : 1 ORDERS` (체결된 지정가 주문의 `filled_order_id`가 그 체결 기록을 가리킴)
- `AI_INSIGHTS 1 : N DAILY_PICKS`

---

## 4. MyBatis 매퍼 작성 시 참고사항

- **N+1이 구조적으로 발생하지 않습니다.** JPA는 연관관계를 지연 로딩하다가 실수로 N+1을 유발하지만, MyBatis는 SQL을 직접 쓰기 때문에 애초에 "따로 조회"가 자동으로 생기지 않습니다. 대신 **매퍼 XML에 JOIN을 빠뜨리지 않고 직접 작성하는 책임**이 개발자에게 있습니다. 다만 이 프로젝트는 조회 대상이 소수(보유 종목 수 등)라 별도 조회 후 애플리케이션에서 `Map`으로 합치는 방식(`StockMapper.findAllByCodes`)도 실용적으로 허용합니다.
  ```xml
  <!-- HoldingMapper.xml: 계좌의 유효 보유종목 조회 -->
  <select id="findByAccountIdAndQuantityGreaterThan" resultMap="HoldingResultMap">
    SELECT id, account_id, stock_code, quantity, avg_price, updated_at
    FROM holdings
    WHERE account_id = #{accountId} AND quantity > #{quantity}
  </select>
  ```
- **금액 컬럼(`avg_price`, `filled_price`, `cash_balance`, `total_value` 등)은 반드시 `resultMap`에서 `BigDecimal`로 매핑.** MyBatis는 JPA처럼 자동 타입 매핑을 해주지 않으므로, `javaType="java.math.BigDecimal"`을 명시적으로 지정해야 함.
- **PK는 Oracle IDENTITY 컬럼 + MyBatis `useGeneratedKeys="true" keyProperty="id"`** 조합으로 처리 (시퀀스+트리거 방식보다 최신 문법)
- **`AI_INSIGHTS.sources`(JSON)는 커스텀 `TypeHandler`** 필요 — `List<InsightSource>` ↔ JSON 문자열 변환을 직접 구현 (`InsightSourceListTypeHandler`, Jackson 기반)
- **도메인 객체는 JPA dirty-checking이 없다.** `Account.debit()`, `Holding.applyBuy()` 같은 메서드로 메모리 상 객체를 바꾼 뒤에는 서비스 레이어에서 해당 매퍼의 `update()`를 명시적으로 호출해야 DB에 반영된다.

---

## 5. 트랜잭션 경계

MyBatis는 JPA와 달리 변경사항을 자동으로 모아 처리하지 않으므로, 여러 테이블에 걸친 쓰기 작업은 서비스 레이어에서 `@Transactional`로 명시적으로 묶어야 합니다.

| 작업 | 트랜잭션에 포함되는 매퍼 호출 |
|---|---|
| 시장가 주문 / 조건을 이미 만족한 지정가 주문 (`POST /api/orders`) | `AccountMapper.findByIdForUpdate` → `HoldingMapper.findByAccountIdAndStockCodeForUpdate`(락 포함) → `AccountMapper.update` → `HoldingMapper.update` → `OrderMapper.insert` |
| 조건 미충족 지정가 주문 접수 (`POST /api/orders`) | `AccountMapper.findByIdForUpdate` → `PendingOrderMapper.sumReservedCash`/`sumReservedQuantity`(가용 잔고 검증) → `PendingOrderMapper.insert` (ORDERS는 건드리지 않음) |
| 지정가 대기 주문 체결 (`OrderMatchingBatchService` → `OrderService.fillPendingOrder`) | `PendingOrderMapper.findByIdForUpdate` → `AccountMapper.findByIdForUpdate` → `HoldingMapper.findByAccountIdAndStockCodeForUpdate` → `AccountMapper.update` → `HoldingMapper.update` → `OrderMapper.insert` → `PendingOrderMapper.update`(PENDING→FILLED) |
| 지정가 대기 주문 취소 (`DELETE /api/orders/{orderId}`) | `PendingOrderMapper.findByIdAndAccountIdForUpdate` → `PendingOrderMapper.update`(PENDING→CANCELLED) — ACCOUNTS/HOLDINGS는 건드리지 않음 |
| 회원가입 | `UserMapper.insert` → `AccountMapper.insert` (계좌 자동 생성) |
| AI 인사이트 배치 | 종목별 `AiInsightMapper.insert` → 계좌별 `DailyPickMapper.insert` |
| 장마감 스냅샷 배치 | 종목별 `PriceSnapshotMapper.insert` + 계좌별 `AccountSnapshotMapper.insert` |

트랜잭션 격리 수준은 Oracle 기본값인 `READ COMMITTED`를 사용합니다. 동시성 제어는 격리 수준이 아니라 `ACCOUNTS`/`HOLDINGS` 행 단위의 `SELECT ... FOR UPDATE` 락으로 처리하므로, 더 높은 격리 수준(`SERIALIZABLE`)은 불필요한 성능 손해로 판단해 채택하지 않았습니다.

---

## 배치 작업 요약

| 배치 | 주기 | 대상 테이블 |
|---|---|---|
| 시세 갱신 | 평일 장중 10분 간격 | `STOCKS.current_price` |
| 등락률·거래량 순위 갱신 | 평일 장중 10분 간격 + 앱 기동 시 1회 | `TOP_MOVERS`, (신규 종목 시) `STOCKS` |
| 가격/자산 스냅샷 | 1일 1회 (장마감 후, 16:00 KST) | `PRICE_SNAPSHOTS`, `ACCOUNT_SNAPSHOTS` |
| AI 인사이트 생성 | 1일 1회 (08:00 KST) | `AI_INSIGHTS`, `DAILY_PICKS` (뉴스 검색+LLM 요약을 Spring Boot 내부에서 직접 호출) |

## 6. 다음 단계 체크리스트

- [x] Oracle DDL 스크립트 작성 (Flyway로 버전 관리 — `backend/src/main/resources/db/migration/V1__init.sql`)
- [ ] Oracle Free(로컬 `gvenzl/oracle-free` 컨테이너)로 실제 스키마 생성 및 매퍼 동작 검증 (현재는 H2 Oracle 근사 모드로만 단위테스트 검증됨)
- [ ] 각 테이블의 인덱스 최종 확정
- [x] 주문/체결 분리 및 지정가 대기 주문 취소 API/UI 구현 (`PENDING_ORDERS`, `OrderMatchingBatchService`)
- [ ] Oracle Cloud Autonomous Database(Always Free) 인스턴스 프로비저닝
