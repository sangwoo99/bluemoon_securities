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
