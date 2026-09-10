# 트러블슈팅

로컬 개발/배포 과정에서 실제로 겪은 이슈와 원인·해결 과정을 기록합니다.
시간순으로 아래에 추가합니다. (면접에서 트러블슈팅 경험을 물어볼 때 참고용으로도 활용)

---

## 1. `backend/Dockerfile`이 Java 17 toolchain과 다른 JDK 버전으로 빌드됨

- **증상**: 빌드 자체는 되지만 `build.gradle`의 `JavaLanguageVersion.of(17)` toolchain 설정과 Dockerfile의 베이스 이미지 버전이 불일치.
- **원인**: `backend/Dockerfile`이 `gradle:8.10-jdk21-alpine`(빌드 스테이지) / `eclipse-temurin:21-jre-alpine`(런타임 스테이지)를 쓰고 있었음. Gradle toolchain이 17을 요구하므로, 이미지에 17이 없으면 Gradle이 빌드 중 JDK 17을 별도로 자동 다운로드하려 시도 — 네트워크 상황에 따라 느려지거나 실패할 수 있음.
- **해결**: 두 스테이지 모두 `jdk17-alpine` / `17-jre-alpine`으로 통일 (`backend/Dockerfile`).

## 2. `docker compose up -d --build` 시 backend가 Flyway 마이그레이션에서 즉시 죽음 (`ORA-03076`)

- **증상**: `Migration V1__init.sql failed` / `ORA-03076: unexpected item DEFAULT in a column definition or inline constraint`. backend 컨테이너가 기동 직후 크래시.
- **원인**: `db/migration/V1__init.sql`의 여러 컬럼 정의가 `TIMESTAMP NOT NULL DEFAULT SYSTIMESTAMP` 순서로 되어 있었음. Oracle은 컬럼 정의에서 `DEFAULT`가 인라인 제약조건(`NOT NULL` 등)보다 **앞**에 와야 함 (`DEFAULT expr [NOT NULL]` 순서). `holdings.quantity`/`avg_price`처럼 순서가 맞는 줄도 섞여 있어서 처음엔 원인 파악이 헷갈렸음.
- **해결**: `NOT NULL DEFAULT SYSTIMESTAMP` → `DEFAULT SYSTIMESTAMP NOT NULL`로 전체 수정.
- **부수 조치**: 실패한 마이그레이션 시도가 `flyway_schema_history`에 실패 기록을 남겨서, SQL을 고친 뒤에도 재시도 시 "Detected failed migration to version 1" 검증 에러가 남아있었음. 실제 테이블은 하나도 안 만들어진 상태(데이터 없음)라 `docker volume rm ai_wts_oracle_data`로 Oracle 볼륨을 통째로 지우고 처음부터 재초기화해서 해결.

## 3. `.gitignore`가 `docker-compose.yml` 자체를 무시하고 있었음

- **증상**: (발견 당시엔 증상 없음 — 코드 리뷰 중 발견) `git add`를 했을 때 인프라 정의 파일 자체가 커밋 대상에서 빠지는 상황.
- **원인**: `.gitignore` 맨 끝에 `/docker-compose.yml`이 들어있었음. 이 파일엔 실제 비밀번호가 없고(전부 `${VAR}` 환경변수 참조) 전체 오케스트레이션 설정의 핵심 파일이라, 실수로 들어간 규칙으로 판단.
- **해결**: 해당 줄 삭제. (`.env`는 계속 정상적으로 무시되고, `.env.example`만 `!.env.example`로 추적됨 — 이건 의도된 정상 설정)
- **덤**: 같은 파일의 `backend/!gradle/wrapper/gradle-wrapper.jar` 줄도 `.gitignore` 문법상 `!`는 줄 맨 앞에 와야 negation으로 동작하는데 깨진 문법이라 아무 효과가 없던 죽은 줄이었음 — 같이 정리.

## 4. `GlobalExceptionHandler`가 예외를 로그 없이 그냥 삼킴

- **증상**: API가 500(`INTERNAL_ERROR`)을 반환하는데 `docker compose logs backend`에 아무 에러 로그도 안 남아서 원인 파악이 불가능했음.
- **원인**: `common/GlobalExceptionHandler.java`의 `handleUnexpected(Exception e)`가 `ApiResponse.fail(...)`만 반환하고 `log.error` 같은 로깅 호출이 아예 없었음.
- **해결**: SLF4J `Logger`를 추가해 `log.error("Unhandled exception", e)`로 스택트레이스를 남기도록 수정. (운영 관점에서도 원래 있어야 했던 로깅 — 디버깅용 임시 조치가 아니라 영구 수정)

## 5. Oracle + MyBatis `useGeneratedKeys`가 IDENTITY 값 대신 ROWID를 반환 (`NumberFormatException`)

- **증상**: 회원가입(`POST /api/auth/signup`)이 500 에러. 로그(4번 수정 후 확인 가능해짐): `NumberFormatException: Character A is neither a decimal digit number...` → `Invalid conversion requested` → `Jdbc3KeyGenerator` 관련 스택트레이스.
- **원인**: 각 매퍼 XML의 `<insert useGeneratedKeys="true" keyProperty="id">`에 `keyColumn` 속성이 없었음. `keyColumn`이 없으면 MyBatis가 내부적으로 `connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)`를 호출하는데, **Oracle JDBC 드라이버는 이 경우 실제 IDENTITY 값이 아니라 ROWID 문자열(`"AAAehj..."` 형태)을 돌려줌**. 이걸 `Long`(NUMBER)으로 파싱하려다 실패한 것. MySQL/H2/PostgreSQL에선 안 나는, Oracle 고유의 잘 알려진 함정.
- **해결**: `UserMapper.xml`, `AccountMapper.xml`, `HoldingMapper.xml`, `OrderMapper.xml`, `AiInsightMapper.xml`, `DailyPickMapper.xml`, `AccountSnapshotMapper.xml`, `PriceSnapshotMapper.xml` 8개 파일 전부 `keyColumn="id"` 추가. `keyColumn`을 명시하면 MyBatis가 `connection.prepareStatement(sql, new String[]{"id"})`를 호출해 Oracle이 실제 컬럼 값을 돌려줌.
- **교훈**: Oracle IDENTITY + MyBatis 조합에서는 `useGeneratedKeys="true" keyProperty="id"`만으로는 부족하고 **`keyColumn`을 항상 같이 명시**해야 함. (`CLAUDE.md`에 절대 규칙으로 추가할 가치 있음)

## 6. 프론트엔드에서 데이터가 하나도 안 보임 (403)

- **증상**: 백엔드가 정상 기동됐는데도 대시보드 등에서 데이터가 전혀 안 나옴. `curl localhost:8080/api/stocks` → `403`.
- **원인**: `SecurityConfig.java`에서 `/api/auth/**`와 `/actuator/health`만 `permitAll`이고 나머지는 전부 인증 필요. 그런데 프론트엔드에는 아직 로그인/회원가입 화면이 없어서(README "아직 안 한 것" 참고) `accessToken` 쿠키가 애초에 존재하지 않았음. API 키(OpenAI/KIS) 문제가 아니었음.
- **해결**: `frontend/app/login/page.tsx`(로그인/회원가입 화면), `frontend/proxy.ts`(구 `middleware.ts` — 미인증 접근을 `/login`으로 리다이렉트)로 해결. Sidebar에 로그아웃 버튼 추가.

## 7. `npm run build` 실패 — `cookies()` 비동기 전환 (Next.js 16)

- **증상**: 로그인 화면 추가 후 빌드하니 `lib/api.ts(10,27): error TS2339: Property 'get' does not exist on type 'Promise<ReadonlyRequestCookies>'`.
- **원인**: `frontend/package.json`이 `"next": "^16.3.4"`로 caret 범위 지정되어 있어 실제로는 **Next.js 16**이 설치됨 (문서상 "Next.js 14"라고 되어 있는 것과 실제 설치 버전이 어긋나 있었음 — 이 문서 불일치 자체는 아직 미해결). Next.js 15부터 `next/headers`의 `cookies()`가 비동기(Promise)로 바뀌었는데, `lib/api.ts`의 `authHeader()`는 동기 방식(`cookies().get(...)`)으로 쓰고 있었음.
- **해결**: `authHeader()`를 `async`로 바꾸고 `(await cookies()).get(...)`로 수정, 호출부(`apiGet`, `apiPost`)에서도 `await authHeader()`로 변경.
- **덤**: Next.js 16에서 `middleware.ts` 컨벤션 자체도 deprecated되어 `proxy.ts`로 이름이 바뀜 (빌드 시 경고). 공식 코드모드(`npx @next/codemod@canary middleware-to-proxy .`)로 `middleware.ts` → `proxy.ts`, `export function middleware` → `export function proxy`로 마이그레이션.
- **미해결로 남겨둠**: `package.json`의 `next` 버전을 실제로 14로 고정할지, 문서(`docs/PRD.md` 등)를 16 기준으로 갱신할지는 별도 결정 필요.

## 8. 보유 종목/거래 내역이 0개인 신규 계정에서 대시보드·거래내역이 500

- **증상**: 로그인은 되는데 `/api/portfolio/summary`, `/api/orders`(거래내역)이 `ORA-00936: missing expression`으로 500. 회원가입 직후(=아직 한 번도 매수 안 한) 모든 계정에서 100% 재현됨 — API 키(KIS/OpenAI) 문제가 아니었음.
- **원인**: `PortfolioService.getSummary()`와 `OrderService.getOrderHistory()`가 각각 보유 종목/주문 내역에서 뽑은 종목 코드 리스트로 `StockMapper.findAllByCodes(codes)`를 호출하는데, 리스트가 **비어있으면** MyBatis `<foreach>`가 `WHERE code IN ()`을 생성함 — Oracle(및 대부분의 DB)에서 빈 `IN ()`은 SQL 문법 오류. 보유 종목이 0개(신규 가입 직후)이거나 주문 내역이 0건이면 항상 이 리스트가 비어있어서, **사실상 모든 신규 유저의 첫 대시보드 진입에서 100% 재현**되는 버그였음.
- **해결**: 두 호출부 모두 코드 리스트가 비어있으면 `findAllByCodes`를 호출하지 않고 바로 빈 `Map`을 사용하도록 가드 추가 (`PortfolioService.java`, `OrderService.java`).
- **교훈**: MyBatis `<foreach>`로 `IN` 절을 만들 때 컬렉션이 비어있을 수 있는 호출부는 반드시 호출 전에 빈 리스트를 걸러낼 것. XML의 `<foreach>` 자체는 빈 컬렉션을 막아주지 않음.

## 9. KIS 시세 조회 배치가 종목 5개 중 일부만 성공 (`EGW00201`)

- **증상**: `KisClient` + `PriceUpdateBatchService` 구현 후 실제 KIS 모의투자 계좌로 테스트하니, 5개 종목 중 처음 1~2개만 갱신되고 나머지는 500 에러로 실패. `WebClientResponseException`의 `getMessage()`만 로깅했더니 "500 Internal Server Error from GET ..."만 찍혀서 원인을 알 수 없었음.
- **원인**: `WebClientResponseException.getResponseBodyAsString()`으로 실제 응답 본문을 로깅하도록 고친 뒤에야 확인: `{"rt_cd":"1","msg1":"초당 거래건수를 초과하였습니다.","msg_cd":"EGW00201"}`. KIS 모의투자 계좌는 초당 호출 건수 제한이 있고, 루프에서 5개 종목을 딜레이 없이 연속 호출하니 3번째 요청부터 걸림. 300ms 간격을 줘도 여전히 실패해서(제한이 초 단위 버킷으로 리셋되는 것으로 추정), 1.1초 간격으로 늘리자 5개 전부 성공.
- **해결**: `PriceUpdateBatchService`에 종목 사이 `Thread.sleep(1100)` 추가. `KisClient`의 예외 로깅도 `WebClientResponseException`을 별도로 잡아 `getResponseBodyAsString()`을 남기도록 수정 (일반 `e.getMessage()`만으로는 KIS의 실제 에러 사유가 안 보임).
- **교훈**: 외부 API 연동 시 `WebClientResponseException`은 `getMessage()`가 아니라 `getResponseBodyAsString()`을 로깅해야 실제 원인(에러 코드/메시지)이 보인다. 또한 배치에서 외부 API를 루프로 여러 번 호출할 때는 그 API의 초당/분당 호출 제한을 반드시 확인하고 간격을 둘 것 — KIS는 접근토큰 발급(분당 1회)과 시세 조회(초당 N회) 각각 별도의 제한이 있음.
- **검증**: 임시로 `@Scheduled` 크론을 매분 실행으로 바꿔 실제 동작을 확인한 뒤(5종목 전부 실제 KIS 시세로 갱신됨을 DB로 확인), 원래 스케줄(평일 장중 10분 간격)로 되돌림.

## 10. 신규 계정은 매수 화면에 종목이 하나도 안 보임 (첫 거래 자체가 불가능)

- **증상**: `/trade` 페이지가 `/api/holdings`(보유 종목)만 가져와서 매수 드롭다운을 채우고 있었음. 신규 계정은 보유 종목이 0개이므로 드롭다운이 비어있고, 페이지 자체가 "거래 가능한 종목이 없습니다"로 막힘 — **신규 유저는 첫 매수를 할 방법이 원천적으로 없었음**. `/stocks`, `HoldingsTable`의 "매수하러 가기" 링크도 전부 이 막다른 흐름으로 연결됨.
- **원인**: 매수(BUY)는 "내가 가진 종목"이 아니라 "시장에 있는 전체 종목" 중에서 골라야 하는데, 프론트가 이 둘을 구분하지 않고 `holdings`만 썼음. 게다가 백엔드에도 전체 종목 목록을 반환하는 `GET /api/stocks`(목록) 엔드포인트 자체가 없었음 (`/api/stocks/{code}` 단건 조회만 존재).
- **해결**:
  - 백엔드: `GET /api/stocks` 목록 엔드포인트 신규 추가 (`StockController`, `StockService.getAllStocks()`), `docs/api-spec.md`에도 반영
  - 프론트: `/trade` 페이지가 `/api/stocks`(전체)와 `/api/holdings`(보유)를 함께 조회하도록 변경. `TradeForm`은 매수 탭에서는 전체 종목을, 매도 탭에서는 보유 종목만 드롭다운에 보여주도록 리스트를 분리(`buyList`/`sellList`). 매수↔매도 탭 전환 시 현재 선택된 종목이 새 목록에 없으면 자동으로 첫 종목으로 전환
- **교훈**: "보유 종목 조회 API"와 "매수 가능 종목 조회"를 같은 API로 취급하면 신규 유저의 온보딩 경로 자체가 막힌다. 화면 설계 시 "이 데이터가 없는 게 정상인 상태(빈 보유)"와 "이 화면이 애초에 의존하면 안 되는 데이터"를 구분해야 함.

## 11. 매수 주문이 전부 500 (`Invalid column type: 1111`)

- **증상**: 10번 문제를 고치고 실제로 첫 매수(시장가)를 시도하니 `POST /api/orders`가 500. 로그: `Invalid column type: 1111` (`java.sql.Types.OTHER`), `Error setting null for parameter #6`.
- **원인**: `ORDERS.limit_price`는 시장가 주문일 때 항상 `null`인데, `OrderMapper.xml`의 INSERT문에서 `#{limitPrice}`에 `jdbcType`을 명시하지 않음. MyBatis는 파라미터 값이 `null`이면 타입을 추론할 수 없어 기본값(`JdbcType.OTHER`)으로 `setNull()`을 호출하는데, Oracle JDBC 드라이버는 이 타입 코드(1111)를 거부함. `cancel_of_order_id`도 nullable인데 같은 문제가 잠재해 있었음(취소 기능 미구현이라 아직 트리거 안 됐을 뿐).
- **해결**: `#{limitPrice, jdbcType=DECIMAL}`, `#{cancelOfOrderId, jdbcType=BIGINT}`로 명시.
- **교훈**: MyBatis + Oracle 조합에서 **nullable 컬럼에 바인딩하는 파라미터는 반드시 `jdbcType`을 명시**할 것. NOT NULL 컬럼은 항상 값이 있어 문제가 안 되지만, nullable 컬럼은 정상 케이스(값 있음)로만 테스트하면 이 버그가 안 드러나고 실제로 null이 들어가는 순간(예: 시장가 주문)에만 터진다 — 이번에도 하필 가장 흔한 케이스(시장가 매수)가 첫 실패 지점이었음.
