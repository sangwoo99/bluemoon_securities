# 블루문 — RAG 인사이트 기반 모의투자 포트폴리오 트래커

단순 CRUD를 넘어 금융 도메인 특유의 트랜잭션 무결성·동시성 처리와, RAG 기반 AI 인사이트 파이프라인을 함께 다루는 포트폴리오 프로젝트입니다.

## 아키텍처

```
[Next.js Frontend]  ---REST--->  [Spring Boot 백엔드 (MyBatis)]  ---내부 API--->  [Python RAG 서비스 (FastAPI)]
     (Vercel)                    (오라클 클라우드 VM)                              (오라클 클라우드 VM, 동일 서버)
                                        |                                              |
                                [Oracle Database]                              [Chroma 벡터 DB]
                                (Autonomous DB, Always Free)                          |
                                        |                                    [네이버 뉴스 검색 API]
                                    [Redis]                                  (RAG용 근거 기사 수집)
                              (시세 캐싱, 재고 락)                                      |
                                        |                                       [OpenAI API]
                              [한국투자증권 Open API]                          (임베딩 + 요약 생성)
                                 (모의투자, 시세 조회)
```

| 레이어 | 기술 |
|---|---|
| 프론트엔드 | Next.js 14 (App Router), TypeScript, recharts |
| 백엔드 | Spring Boot 3.x, **Java 17**, **MyBatis**, Spring Security |
| RAG 서비스 | Python 3.11+, FastAPI, LangChain, Chroma |
| DB / 캐시 | **Oracle Database**(Free 23c), Redis |
| 외부 연동 | 한국투자증권 KIS Developers (모의투자, 시세), 네이버 뉴스 검색 API (RAG 근거 자료 수집), OpenAI API (임베딩 + 요약) |
| 인프라 | Docker Compose, Nginx, Vercel(FE) + 오라클 클라우드 Always Free(BE+RAG) |

> **스택 전환 이력**: 채용 시장 분석 결과, 전통 금융권(계정계) 채용 수요에 맞춰 `Spring Data JPA + QueryDSL + PostgreSQL + Java 21` → `MyBatis + Oracle Database + Java 17`로 전환했습니다. 문서상 요구된 "Java 11"은 Spring Boot 3.x 최소 요구 버전(17)과 양립 불가능해 17로 조정했습니다 (자세한 트레이드오프는 `docs/PRD.md` PART 2 참고).

상세 설계는 `docs/`를 참고하세요.

- [`docs/PRD.md`](docs/PRD.md) — 요구사항 정의, 기술 스택 선정 이유
- [`docs/storyboard.md`](docs/storyboard.md) — 화면별 요구사항
- [`docs/db-schema.md`](docs/db-schema.md) — DB 스키마, 동시성 처리 지점
- [`docs/api-spec.md`](docs/api-spec.md) — REST API 명세

## 저장소 구조

```
/frontend       Next.js 14 (App Router), TypeScript
/backend        Spring Boot 3.x, Java 17, Gradle, MyBatis
/rag-service    Python 3.11+, FastAPI, LangChain
/docs           설계 문서
/nginx          리버스 프록시 설정 (VM 배포용)
```

## 핵심 설계 결정 & 트레이드오프

- **MyBatis + Oracle로 전환**: 전통 금융권(계정계 중심) 채용 물량이 신생 핀테크보다 많다고 판단해 JPA+QueryDSL+PostgreSQL 대신 실제 현업에서 더 흔히 쓰이는 조합으로 전환. SQL을 직접 작성해 N+1이 구조적으로 발생하지 않음.
- **"Java 11" 대신 Java 17**: Spring Boot 3.x는 Java 17 이상을 요구해 두 요구사항이 양립 불가능. Spring Boot 3.x를 유지하는 쪽을 택함.
- **WebSocket 대신 REST 폴링 + 캐싱**: 무료 호스팅 환경에서 상시 연결 유지가 어렵고, 실제 사용 패턴(사용자가 보고 있을 때만 갱신 필요)을 고려해 의도적으로 선택.
- **RAG를 별도 Python 마이크로서비스로 분리**: Spring AI/LangChain4j보다 LangChain(Python) 생태계가 성숙해 구현 리스크가 낮음. Spring Boot는 RAG 서비스를 내부 API로만 호출하고 외부에 노출하지 않음.
- **AI 인사이트는 배치로만 생성, 요청 경로에서는 캐시 조회만**: 응답 지연·LLM 비용을 없애고, 장애 격리(RAG 서비스가 죽어도 대시보드는 정상 동작)를 확보.
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
| rag-service | ✅ | 외부(호스트) 포트 미노출 — backend가 내부 네트워크로만 호출 |
| backend | ✅ | `localhost:8080` |
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

### RAG 서비스만 따로 돌리고 싶다면

```bash
cd rag-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

## 배포

- 프론트: Vercel (Git 연동 자동 배포)
- 백엔드 + RAG 서비스: 오라클 클라우드 Always Free VM에 `docker compose up -d --build`로 함께 배포, Nginx가 `/api`를 백엔드로 리버스 프록시
- UptimeRobot으로 헬스체크 핑을 보내 인스턴스 유휴 회수 방지

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

### 검증 완료

- `docker compose up -d --build` 로 oracle/redis/rag-service/backend/nginx 전체 기동 확인 — `curl localhost:8080/actuator/health` → `{"status":"UP"}`
- `cd frontend && npm run build` 정상 통과, `npm run dev`로 로그인 → 대시보드 데이터 조회까지 end-to-end 확인
- 프론트 로그인/회원가입 화면(`/login`) 구현 — 인증 없이 접근 시 자동으로 `/login`으로 리다이렉트, 로그아웃 버튼 포함
- 과정에서 실제로 걸렸던 버그들 수정 — 상세 원인/해결은 [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) 참고 (Dockerfile JDK 버전 불일치, `V1__init.sql` 컬럼 정의 순서, Oracle+MyBatis `useGeneratedKeys` ROWID 문제, Next.js 16 `cookies()` 비동기 전환, KIS 초당 호출 제한, 신규 계정 첫 매수 불가 문제, nullable 컬럼 `jdbcType` 누락 등)
- KIS 모의투자 앱키로 실제 토큰 발급 + 시세 조회 API 호출 성공 확인, 배치를 통해 5개 시드 종목 전부 실제 KIS 시세로 갱신되는 것까지 end-to-end 검증
- 신규 계정 기준 전체 골든 패스 검증: 회원가입 → 로그인 → `/trade`에서 종목 선택(전체 종목 목록) → 시장가/지정가 매수 → 보유종목·포트폴리오 요약에 정확히 반영

### 검증 필요 (이 환경에서 직접 빌드/실행하지 못함)

- `cd backend && ./gradlew build` — 컴파일 에러·테스트(H2 Oracle 근사 모드) 통과 여부 확인
- `cd rag-service && pip install -r requirements.txt && pytest`
- 실제 `OPENAI_API_KEY`/`NAVER_CLIENT_*` 키로 AI 인사이트 배치·RAG 파이프라인 end-to-end 확인

### 아직 안 한 것

- **`package.json`의 Next.js 버전 정리**: `"next": "^16.3.4"`로 실제 16이 설치되는데 문서(`docs/PRD.md` 등)는 "Next.js 14" 기준 — 버전을 14로 고정할지 문서를 16 기준으로 갱신할지 결정 필요 (`TROUBLESHOOTING.md` 7번 참고)
- **주문 취소 기능**: DB 스키마(`cancel_of_order_id`)만 대비해두고 API/UI 미구현
- **실제 배포**: Vercel(프론트), 오라클 클라우드 VM(백엔드+RAG), UptimeRobot 헬스체크 — 아직 로컬 스캐폴딩 단계
- **OpenAI / 네이버 뉴스 API 키 발급 및 실제 테스트**: 코드는 있으나 실제 키로 RAG 파이프라인 end-to-end 검증 안 됨
- **README 최상단 "성공 기준" 체크리스트**(`docs/PRD.md` 6장) 항목별 검증
