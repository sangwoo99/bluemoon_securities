# 블루문 — AI 인사이트 기반 모의투자 포트폴리오

단순 CRUD를 넘어 금융 도메인 특유의 트랜잭션 무결성·동시성 처리와, AI 인사이트 파이프라인을 함께 다루는 포트폴리오 프로젝트입니다.

## 아키텍처

```
[Next.js Frontend]  ---REST--->  [Spring Boot 백엔드 (MyBatis)]
     (Vercel)                    (오라클 클라우드 VM)
                                        |         \
                                [Oracle Database]   \--- [NewsData.io 뉴스 검색] --- [OpenAI API]
                                (Autonomous DB, Always Free)   (배치 전용 — AI 인사이트 생성)
                                        |
                                    [Redis]
                              (시세 캐싱, 재고 락)
                                        |
                              [한국투자증권 Open API]
                                 (모의투자, 시세 조회)
```

| 레이어 | 기술 |
|---|---|
| 프론트엔드 | Next.js 14 (App Router), TypeScript, recharts |
| 백엔드 | Spring Boot 3.x, **Java 17**, **MyBatis**, Spring Security |
| AI 인사이트 | Spring Boot 내부 (`WebClient`로 NewsData.io 뉴스 검색 + OpenAI Chat Completions 직접 호출) |
| DB / 캐시 | **Oracle Database**(Free 23c), Redis |
| 외부 연동 | 한국투자증권 KIS Developers (모의투자, 시세), NewsData.io (AI 인사이트 근거 자료 수집), OpenAI API (요약 생성) |
| 인프라 | Docker Compose, Nginx, Vercel(FE) + 오라클 클라우드 Always Free(BE) |

> **스택 전환 이력**: 채용 시장 분석 결과, 전통 금융권(계정계) 채용 수요에 맞춰 `Spring Data JPA + QueryDSL + PostgreSQL + Java 21` → `MyBatis + Oracle Database + Java 17`로 전환했습니다. 문서상 요구된 "Java 11"은 Spring Boot 3.x 최소 요구 버전(17)과 양립 불가능해 17로 조정했습니다 (자세한 트레이드오프는 `docs/PRD.md` PART 2 참고).
>
> **AI 인사이트 아키텍처 변경 이력**: 원래는 Python RAG 마이크로서비스(FastAPI+LangChain+Chroma)로 분리했으나, 오라클 클라우드 프리티어 VM에 실제 배포해보니 Python 프로세스까지 함께 띄우기엔 메모리가 부족했습니다. 벡터 검색이 꼭 필요할 만큼 AI 의존도가 높은 기능이 아니라고 판단해 Spring Boot 배치 내부로 통합했습니다. 기존 코드는 [`archive/rag-service`](archive/rag-service/ARCHIVE.md)에 백업, 컴퓨팅 자원 확보 시 재도입 가능.
>
> **뉴스 소스 변경 이력**: 처음엔 네이버 뉴스 검색 API를 썼으나, 네이버가 검색 API 신규 발급을 NCP로 이관하고(2026-07-31) 검색 결과의 AI 입력/요약 활용을 약관으로 금지해(2026-09-07 시행) 지금 하는 일과 정면 충돌했습니다. 대안으로 검토한 구글 뉴스 검색 RSS도 "개인적·비상업적 용도"로만 쓰라는 저작권 문구가 있어, 최종적으로 개인/상업적 이용을 이용약관에 명시적으로 허용하는 NewsData.io(무료 200크레딧/일)로 교체했습니다.

상세 설계는 `docs/`를 참고하세요.

- [`docs/PRD.md`](docs/PRD.md) — 요구사항 정의, 기술 스택 선정 이유
- [`docs/storyboard.md`](docs/storyboard.md) — 화면별 요구사항
- [`docs/db-schema.md`](docs/db-schema.md) — DB 스키마, 동시성 처리 지점
- [`docs/api-spec.md`](docs/api-spec.md) — REST API 명세

## 저장소 구조

```
/frontend            Next.js 14 (App Router), TypeScript
/backend             Spring Boot 3.x, Java 17, Gradle, MyBatis (AI 인사이트 생성 포함)
/docs                설계 문서
/nginx               리버스 프록시 설정 (VM 배포용)
/archive/rag-service (백업, 미사용) Python 3.11+, FastAPI, LangChain — 상세: archive/rag-service/ARCHIVE.md
```

## 핵심 설계 결정 & 트레이드오프

- **MyBatis + Oracle로 전환**: 전통 금융권(계정계 중심) 채용 물량이 신생 핀테크보다 많다고 판단해 JPA+QueryDSL+PostgreSQL 대신 실제 현업에서 더 흔히 쓰이는 조합으로 전환. SQL을 직접 작성해 N+1이 구조적으로 발생하지 않음.
- **"Java 11" 대신 Java 17**: Spring Boot 3.x는 Java 17 이상을 요구해 두 요구사항이 양립 불가능. Spring Boot 3.x를 유지하는 쪽을 택함.
- **WebSocket 대신 REST 폴링 + 캐싱**: 무료 호스팅 환경에서 상시 연결 유지가 어렵고, 실제 사용 패턴(사용자가 보고 있을 때만 갱신 필요)을 고려해 의도적으로 선택.
- **(변경) RAG 마이크로서비스 → Spring Boot 내부 처리**: 처음엔 별도 Python RAG 서비스(FastAPI+LangChain+Chroma)로 분리했으나, 오라클 클라우드 프리티어 VM에 실제 배포해보니 Python 프로세스까지 함께 뜨기엔 메모리가 부족했음. 벡터 검색이 꼭 필요할 만큼 AI 의존도가 높은 기능도 아니라 판단해, `NewsDataClient`+`OpenAiClient`로 Spring Boot 배치 안에서 직접 처리하도록 통합. 기존 코드는 [`archive/rag-service`](archive/rag-service/ARCHIVE.md)에 백업.
- **AI 인사이트는 배치로만 생성, 요청 경로에서는 캐시 조회만**: 응답 지연·LLM 비용을 없애고, 장애 격리(외부 API 호출이 실패해도 대시보드는 정상 동작)를 확보.
- **AI 인사이트를 "추천"이 아닌 "요약 정보"로 프레이밍**: 투자자문업 관련 법적 리스크 회피, 출처 표기와 면책 문구를 응답에 고정.
- **매수/매도 동시성 제어**: `ACCOUNTS.cash_balance`, `HOLDINGS.quantity` 갱신을 `SELECT ... FOR UPDATE` 비관적 락으로 감싸고, 락 획득 순서를 `ACCOUNTS → HOLDINGS`로 고정해 데드락을 방지 (이커머스 재고 관리와 동일 패턴).
- **`ORDERS` 테이블은 append-only**: 거래 이력은 수정·삭제하지 않고, 취소는 별도 레코드로 남겨 감사 추적성을 확보.

## 로컬 개발 환경 실행

### Docker로 되는 것 / 안 되는 것

`docker-compose.yml`은 **백엔드 쪽 전체**(DB부터 API까지)를 컨테이너로 띄웁니다. 프론트엔드는 배포 대상이 Vercel이라 compose에 포함되어 있지 않고, 화면을 보려면 로컬에서 따로 실행해야 합니다.

| 서비스 | `docker compose up` | 비고 |
|---|---|---|
| oracle | ✅ | 최초 기동 시 DB 초기화로 1~2분 이상 걸릴 수 있음 |
| redis | ✅ | |
| backend | ✅ | `localhost:8080` (AI 인사이트도 이 컨테이너 안에서 직접 처리) |
| nginx | ✅ | `localhost:80`, `/api` → backend 리버스 프록시 |
| **frontend** | ❌ **없음** | 아래처럼 `npm run dev`로 별도 실행 필요 |

### 1. 백엔드 전체 스택 (Docker)

```bash
cp .env.example .env   # 값 채우기 — OPENAI_API_KEY/KIS_* 를 비워두면 컨테이너는 뜨지만 AI 인사이트·시세 연동만 동작 안 함
docker compose up -d --build

docker compose ps                     # 전부 Up(healthy)인지 확인
docker compose logs -f oracle         # 최초 기동 시 "DATABASE IS READY TO USE!" 뜰 때까지 대기
curl http://localhost:8080/actuator/health   # {"status":"UP"} 이면 준비 완료
```

코드를 고친 뒤 다시 반영하려면 `docker compose up -d --build`를 재실행 (바뀐 서비스만 재빌드됨).

### 2. 프론트엔드 (Docker 밖, 로컬에서 직접)

```bash
cd frontend
npm install
npm run dev   # http://localhost:3000
```
`NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`(`.env.local`)이 백엔드를 가리켜야 합니다.

### (선택) 백엔드를 컨테이너 없이 JVM에서 직접 돌리고 싶다면

`gradlew`/`gradlew.bat`/`gradle-wrapper.jar`가 저장소에 포함되어 있어 로컬에 Gradle을 따로 설치할 필요는 없습니다. **Java 17**만 있으면 됩니다.

```bash
docker compose up -d oracle redis   # DB/캐시만 컨테이너로
cd backend
./gradlew bootRun
```

## 배포

- 프론트: Vercel (Git 연동 자동 배포)
- 백엔드: 오라클 클라우드 VM에 배포, Nginx가 `/api`를 백엔드로 리버스 프록시 (AI 인사이트도 이 백엔드 안에서 함께 처리되므로 별도 서비스 배포가 필요 없음)
- UptimeRobot으로 헬스체크 핑을 보내 인스턴스 유휴 회수 방지

### 배포용 compose가 로컬 개발용과 다른 점

VM이 **1 OCPU/1GB(AMD 상시 무료)** 처럼 작은 스펙이면 `docker-compose.yml`을 그대로 못 씁니다 — 거기 포함된 Oracle DB 컨테이너(`gvenzl/oracle-free`)만 해도 최소 1.5~2GB는 필요해서, 1GB 밖에 없는 VM에서는 아예 못 뜹니다.

그래서 배포용으로 **`docker-compose.prod.yml`**을 따로 둡니다:

- Oracle DB 컨테이너 없음 — 대신 **Oracle Cloud Autonomous Database(Always Free)**에 연결 (VM의 RAM을 전혀 안 씀, 완전히 분리된 무료 리소스)
- `redis`/`backend`/`nginx` 각각에 `mem_limit`을 걸어 1GB 안에서 나눠 쓰게 함
- `JDK_JAVA_OPTIONS: -XX:MaxRAMPercentage=50.0`으로 JVM 힙을 컨테이너 메모리 제한 기준으로 제한

```bash
cp .env.prod.example .env.prod   # SPRING_DATASOURCE_URL 등 Autonomous DB 연결 정보 채우기
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Autonomous DB 연결 문자열 만드는 방법(지갑 없는 TLS 방식 권장)은 `.env.prod.example` 상단 주석 참고.

## 개발 로드맵 (2주)

| 기간 | 작업 |
|---|---|
| 1~3일차 | DB 스키마, API 명세 확정, Spring Boot 프로젝트 셋업 |
| 4~6일차 | 핵심 CRUD(계좌/보유/매수매도) 백엔드 구현 |
| 7~8일차 | Next.js ↔ Spring Boot 연동 |
| 9~10일차 | 한국투자 API 연동, Python RAG 서비스 구현 |
| 11~12일차 | 배포(오라클 클라우드, Vercel), 캐싱/스케줄링 적용 |
| 13~14일차 | 테스트, 문서화, 버그 수정 |

## 진행 현황

### 완료 (모노레포 스캐폴딩)

- **docs/**: PRD·스토리보드·API 명세 이전, `db-schema.md` 신규 작성(ERD, 락 전략, 배치 요약)
- **frontend**: Next.js 14 App Router로 5개 화면(대시보드/종목상세/매수매도/거래내역/보유종목) 전체 구현. 목업 디자인을 실제 컴포넌트로 포팅, `recharts` 차트, 로딩 스켈레톤·빈 상태·404 처리, 백엔드 API 연동(`{success,data,error}` 컨벤션)
- **backend**: Spring Boot 3.3 / Java 17 / MyBatis. 도메인·매퍼(XML)·서비스·컨트롤러 전 계층 구현. `ACCOUNTS → HOLDINGS` 순서의 비관적 락(`SELECT ... FOR UPDATE`), `BigDecimal` 금액 계산, `ORDERS` append-only, JWT 인증, Flyway Oracle 마이그레이션(V1 스키마 + V2 종목 시드), 동시성 테스트 포함
- **한국투자증권(KIS) 연동**: `KisClient`(OAuth 토큰 발급 + Redis 캐싱, 시세 조회) + `PriceUpdateBatchService`(평일 장중 10분 간격으로 `STOCKS.current_price` 갱신). 요청 경로가 아닌 배치에서만 호출, 시세 조회 엔드포인트만 사용(주문 API는 호출하지 않음 — 모의투자 계좌라도 안전하게)
- **rag-service**: FastAPI + LangChain + Chroma. 네이버 뉴스 검색 → 임베딩 유사도 검색 → gpt-4o-mini 요약, "매수/매도 추천 금지" 프롬프트 제약, Spring Boot 배치 전용 내부 엔드포인트
- **인프라**: `docker-compose.yml`(oracle/redis/backend/rag-service/nginx), `.env.example`, 루트 `CLAUDE.md`
- **스택 전환**: JPA+QueryDSL+PostgreSQL+Java21 → MyBatis+Oracle+Java17 (전통 금융권 채용 시장 대응, `docs/PRD.md` PART 2 참고). 리포지토리 8개 전부 매퍼(인터페이스+XML)로 재작성, 엔티티에서 JPA 어노테이션 제거, 페이징을 Spring Data `Page` 대신 count+list 수동 조합으로 변경.
- **AI 인사이트 아키텍처 전환**: 오라클 클라우드 프리티어 VM 메모리 제약으로 `rag-service`(Python+FastAPI+LangChain+Chroma)를 Spring Boot 내부로 통합. `NewsDataClient`(뉴스 검색) + `OpenAiClient`(Chat Completions 호출) + `InsightGenerationService`(프롬프트 구성·오케스트레이션)로 재구현, `InsightBatchService`는 `RagServiceClient` 대신 이 서비스를 호출하도록 변경. 벡터 검색(Chroma) 없이 뉴스 검색 API 결과 상위 N건을 그대로 컨텍스트로 사용. 기존 `rag-service`는 [`archive/rag-service`](archive/rag-service/ARCHIVE.md)로 백업.
- **뉴스 소스 재교체**: 네이버 뉴스 검색 API → (검토만) 구글 뉴스 RSS → NewsData.io. 네이버가 검색 API를 NCP로 이관하고 AI 활용을 약관으로 금지, 구글 뉴스 RSS는 "개인적 용도"만 허용하는 저작권 문구가 있어 최종적으로 개인/상업적 이용을 명시적으로 허용하는 NewsData.io로 정착.

### 검증 완료

- `docker compose up -d --build` 로 oracle/redis/backend/nginx 전체 기동 확인 — `curl localhost:8080/actuator/health` → `{"status":"UP"}`
- `cd frontend && npm run build` 정상 통과, `npm run dev`로 로그인 → 대시보드 데이터 조회까지 end-to-end 확인
- 프론트 로그인/회원가입 화면(`/login`) 구현 — 인증 없이 접근 시 자동으로 `/login`으로 리다이렉트, 로그아웃 버튼 포함
- 과정에서 실제로 걸렸던 버그들 수정 — 상세 원인/해결은 [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) 참고 (Dockerfile JDK 버전 불일치, `V1__init.sql` 컬럼 정의 순서, Oracle+MyBatis `useGeneratedKeys` ROWID 문제, Next.js 16 `cookies()` 비동기 전환, KIS 초당 호출 제한, 신규 계정 첫 매수 불가 문제, nullable 컬럼 `jdbcType` 누락 등)
- KIS 모의투자 앱키로 실제 토큰 발급 + 시세 조회 API 호출 성공 확인, 배치를 통해 5개 시드 종목 전부 실제 KIS 시세로 갱신되는 것까지 end-to-end 검증
- 신규 계정 기준 전체 골든 패스 검증: 회원가입 → 로그인 → `/trade`에서 종목 선택(전체 종목 목록) → 시장가/지정가 매수 → 보유종목·포트폴리오 요약에 정확히 반영
- 실제 `OPENAI_API_KEY`/`NEWSDATA_API_KEY` 키로 AI 인사이트 배치 end-to-end 확인 — 28개 종목 중 6개(삼성전자·NAVER·카카오·현대차·기아·삼성바이오로직스)에서 실제 뉴스 검색 → 요약 생성 → 종목상세 화면 노출까지 확인. 나머지는 해당 시점에 NewsData.io에 검색되는 한국어 기사가 없어 정상적으로 스킵됨(버그 아님)

### 검증 필요 (이 환경에서 직접 빌드/실행하지 못함)

- `cd backend && ./gradlew build` — 컴파일 에러·테스트(H2 Oracle 근사 모드) 통과 여부 확인 (`NewsDataClient`/`OpenAiClient`/`InsightGenerationService` 추가분 포함)

### 아직 안 한 것

- **`package.json`의 Next.js 버전 정리**: `"next": "^16.3.4"`로 실제 16이 설치되는데 문서(`docs/PRD.md` 등)는 "Next.js 14" 기준 — 버전을 14로 고정할지 문서를 16 기준으로 갱신할지 결정 필요 (`TROUBLESHOOTING.md` 7번 참고)
- **주문 취소 기능**: DB 스키마(`cancel_of_order_id`)만 대비해두고 API/UI 미구현
- **실제 배포**: Vercel(프론트), 오라클 클라우드 VM(백엔드), UptimeRobot 헬스체크 — 아직 로컬 스캐폴딩 단계
- **README 최상단 "성공 기준" 체크리스트**(`docs/PRD.md` 6장) 항목별 검증

## 배치 스케줄

| 배치 | 트리거 | 대상 |
|---|---|---|
| `MarketRankingBatchService` (등락률/거래량 순위) | 앱 기동 시 1회 + 평일 09~15시 10분 간격 | `TOP_MOVERS`, 신규 종목 시 `STOCKS` |
| `PriceUpdateBatchService` (시세 갱신) | 평일 09~15시 10분 간격만 (기동 시 트리거 없음) | `STOCKS.current_price` |
| `SnapshotBatchService` (자산/시세 스냅샷) | 매일 16:00 KST 1회 (장마감 후) | `PRICE_SNAPSHOTS`, `ACCOUNT_SNAPSHOTS` |
| `InsightBatchService` (AI 인사이트) | 매일 08:00 KST 1회만 | `AI_INSIGHTS`, `DAILY_PICKS` |

> 종목 목록(순위)은 재기동 시에만 도는 게 아니라 그 이후로도 평일 장중이면 10분마다 계속 갱신된다. 시세 갱신은 기동 트리거가 없어 평일 장중 첫 10분 주기가 돌기 전까지는 시드 값 그대로 보일 수 있다. AI 인사이트만 하루 딱 한 번(08:00 KST) 돈다.

## 저사양 서버(1 OCPU/1GB) 대응

오라클 클라우드 Always Free의 작은 VM(AMD 1 OCPU/1GB)에도 올라가도록 처음 설계와 다르게 바꾼 부분들:

| 조치 | 내용 | 이유 |
|---|---|---|
| AI 인사이트를 별도 Python RAG 서비스에서 Spring Boot 내부로 통합 | FastAPI+LangChain+Chroma 프로세스 자체를 제거, `NewsDataClient`+`OpenAiClient`로 백엔드 안에서 직접 처리 | 별도 프로세스 하나(+그 프로세스의 파이썬 런타임 메모리)를 통째로 없앰 |
| 벡터 검색(Chroma) 제거 | 뉴스 상위 5건을 그대로 컨텍스트로 사용, 임베딩 계산 없음 | 임베딩 생성/벡터 인덱싱은 메모리·CPU를 꽤 먹는데, 이 기능엔 과한 설계였음 |
| 배포용 `docker-compose.prod.yml` 분리 | Oracle DB 컨테이너(`gvenzl/oracle-free`, 최소 1.5~2GB 필요) 제거, 대신 VM과 완전히 분리된 무료 리소스인 Oracle Cloud **Autonomous Database**에 연결 | DB를 VM 안에 같이 띄우면 1GB로는 시작 자체가 안 됨 |
| 컨테이너별 `mem_limit` 설정 | `redis` 80MB / `backend` 550MB / `nginx` 32MB로 상한을 나눠 걸어둠 (합쳐도 1GB 안쪽) | 한 컨테이너가 메모리를 독차지해서 나머지가 OOM 나는 걸 방지 |
| JVM 힙/GC 튜닝 | `JDK_JAVA_OPTIONS: -XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC` | 힙을 컨테이너 메모리 제한(550MB)의 절반으로 제한하고, 멀티코어를 가정하는 G1/Parallel GC 대신 1 OCPU 환경에 맞는 SerialGC 사용 |
| KIS 호출 사이 간격(`CALL_INTERVAL_MS`) | 배치들이 KIS를 연속 호출할 때 1.1초씩 대기 | 리소스보다는 호출 빈도 제한(EGW00201) 회피 목적이지만, 결과적으로 짧은 시간에 몰아치는 부하도 같이 완화됨 |
