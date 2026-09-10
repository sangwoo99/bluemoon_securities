# CLAUDE.md

이 파일은 Claude Code가 세션 시작 시 자동으로 읽는 프로젝트 컨텍스트입니다.
코드에서 추론할 수 없는 정보만 담습니다 — 나머지는 노이즈입니다.

## 프로젝트

블루문 — RAG 인사이트 기반 모의투자 포트폴리오 트래커. 개발자 채용용 포트폴리오 프로젝트.
상세 요구사항: `docs/PRD.md` / 화면 설계: `docs/storyboard.md` / DB: `docs/db-schema.md` / API: `docs/api-spec.md`

**스택 참고**: 백엔드는 전통 금융권(계정계) 채용 시장에 맞춰 의도적으로 MyBatis + Oracle을 사용합니다. JPA/QueryDSL이 아닙니다.
**Java 버전 참고**: Spring Boot 3.x는 Java 17 이상을 요구하므로 "Java 11" 대신 **Java 17(LTS)** 을 사용합니다 (Spring Boot 3.x + Java 11 조합은 빌드 자체가 불가능함 — README 트레이드오프 참고).

## 저장소 구조 (모노레포)

```
/frontend       Next.js 14 (App Router), TypeScript
/backend        Spring Boot 3.x, Java 17, Gradle, MyBatis
/rag-service    Python 3.11+, FastAPI, LangChain
/docs           설계 문서 (PRD, DB 스키마, API 명세, 스토리보드)
/nginx          리버스 프록시 설정 (VM 배포용)
```

## 빌드 / 테스트 명령어

```bash
# 로컬 개발 시 Oracle Database(Free), Redis 먼저 기동
docker compose up -d oracle redis

# frontend
cd frontend && npm install
cd frontend && npm run dev      # 개발 서버
cd frontend && npm run build    # 프로덕션 빌드 (커밋 전 항상 실행해 타입 에러 확인)

# backend (Java 17)
cd backend && ./gradlew bootRun # 개발 서버
cd backend && ./gradlew test    # 테스트 (H2를 Oracle 근사 모드로 사용)
cd backend && ./gradlew build   # 빌드 (커밋 전 항상 실행)

# rag-service
cd rag-service && pip install -r requirements.txt
cd rag-service && uvicorn main:app --reload --port 8000
cd rag-service && pytest
```

## 절대 규칙 (위반 시 실제 버그로 이어짐)

- **금액(가격, 잔고, 평단가) 계산은 항상 `BigDecimal` 사용. `double`/`float` 절대 금지.** MyBatis는 자동 타입 매핑을 하지 않으므로, 매퍼 XML의 `resultMap`에서 `javaType="java.math.BigDecimal"`을 반드시 명시할 것. Python 쪽도 `Decimal` 사용, `float` 금지.
- **JPA를 쓰지 않습니다. Spring Data JPA, Hibernate, QueryDSL 관련 코드/의존성을 추가하지 말 것.** 모든 DB 접근은 `mapper` 패키지의 MyBatis 매퍼(인터페이스 + XML)로 작성합니다.
- **여러 테이블을 조인해서 보여줘야 하는 조회는 매퍼 XML에 JOIN을 직접 작성할 것.** "일단 하나 조회하고 루프에서 또 조회"하는 패턴(N+1)을 만들지 말 것 — MyBatis는 자동으로 이런 문제가 생기지 않지만, 코드를 그렇게 짜면 똑같이 발생함.
- **도메인 객체를 변경(`debit`/`credit`/`applyBuy`/`applySell` 등)한 뒤에는 반드시 해당 매퍼의 `update()`를 명시적으로 호출할 것.** MyBatis는 JPA와 달리 dirty-checking이 없어 자동 반영되지 않는다.
- **`HOLDINGS.quantity`, `ACCOUNTS.cash_balance` 갱신은 반드시 락으로 감쌀 것.** Oracle의 `SELECT ... FOR UPDATE` 문법을 매퍼 XML에 명시적으로 작성하고, 락 획득 순서는 항상 `ACCOUNTS → HOLDINGS` (데드락 방지).
- **`ORDERS` 테이블은 append-only.** UPDATE/DELETE 금지, 취소는 별도 레코드로 추가.
- **AI 인사이트는 실시간 생성 금지.** `/api/insights/*`는 항상 `AI_INSIGHTS` 테이블 캐시를 조회만 하고, 생성은 배치(하루 1회)에서만 수행. LLM을 요청 경로에서 직접 호출하는 코드를 작성하지 말 것.
- **AI 인사이트 문구는 "추천"이 아닌 "요약 정보"로 작성.** "매수하세요" 같은 단정적 문구 금지 (투자자문업 이슈, `docs/PRD.md` 참고). 응답에 항상 면책 문구 포함.
- **PK는 Oracle IDENTITY 컬럼 사용.** 시퀀스+트리거 방식으로 되돌리지 말 것 (12c 이상이므로 IDENTITY 문법 사용 가능).
- **여러 매퍼 호출이 하나의 논리적 작업(예: 주문 처리 = ORDERS insert + HOLDINGS update + ACCOUNTS update)이면 서비스 레이어에 `@Transactional`을 반드시 붙일 것.** MyBatis는 JPA처럼 변경사항을 자동으로 모아뒀다가 처리하지 않으므로, 트랜잭션 경계를 직접 명시하지 않으면 일부만 커밋되는 정합성 버그가 생김.

## API 응답 컨벤션

모든 API는 `{ success, data, error }` 래퍼를 사용합니다 (상세: `docs/api-spec.md`).
컨트롤러에서 이 래퍼를 매번 손으로 만들지 말고, 공통 `ApiResponse<T>` 래퍼 클래스 + `@RestControllerAdvice` 예외 핸들러로 처리합니다.

## RAG 서비스 연동

Spring Boot는 Python RAG 서비스를 내부 API로만 호출합니다 (`docs/api-spec.md` 6장).
RAG 서비스를 외부(프론트엔드)에 직접 노출하지 않습니다 — 반드시 Spring Boot를 거칩니다.

## 배포 환경

- 프론트: Vercel
- 백엔드 + RAG 서비스: 오라클 클라우드 VM 1대에 Docker Compose로 함께 배포
- DB: Oracle Cloud Autonomous Database (Always Free) — 로컬 개발은 `gvenzl/oracle-free`(Docker, 로그인 불필요한 Oracle Free 23c 커뮤니티 이미지)
- 로컬 개발 시에도 `docker-compose.yml`로 Oracle, Redis를 띄우고 시작 (개별 설치 지양)

## 코드 스타일

- Java: 필드/메서드는 카멜케이스, DTO는 `~Request`/`~Response` 접미사, 패키지 루트는 `com.bluemoon.backend`
- MyBatis 매퍼: `XxxMapper.java`(인터페이스) + `XxxMapper.xml`(SQL)은 항상 같은 이름으로 쌍을 맞출 것
- 커밋 메시지: `feat:`, `fix:`, `refactor:` 등 conventional commits 형식 (면접에서 커밋 히스토리를 보는 경우 대비)
