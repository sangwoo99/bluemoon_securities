# Vercel 프론트엔드 배포 가이드

`/frontend`(Next.js)를 Vercel에 배포하는 과정 정리. 백엔드/DB 배포는 `오라클_클라우드_배포_가이드.md` 참고.

---

## 1. Vercel 가입 & GitHub 연결

- 이메일로 가입한 경우, 계정에 연결된 GitHub 계정이 원하는 계정이 아닐 수 있음

### 연결된 GitHub 계정 바꾸기
1. **브라우저에서 먼저 GitHub 로그아웃**(또는 원하는 GitHub 계정으로 로그인) — Vercel이 OAuth로 GitHub 연결할 때 현재 브라우저 세션의 GitHub 계정을 그대로 쓰기 때문
2. Vercel 대시보드 → 우측 상단 프로필 → **Settings**(계정 설정, 프로젝트 설정 아님)
3. 왼쪽 메뉴 **"Login Connections"** → GitHub 항목 **Disconnect**
4. 다시 **Connect** → 원하는 GitHub 계정으로 로그인/승인
5. 그래도 원하는 레포가 안 보이면 GitHub 쪽에서 확인: https://github.com/settings/installations → **Vercel** 앱 → **Configure**에서 접근 권한 레포에 대상 레포 추가

---

## 2. 프로젝트 Import

1. Vercel → **Add New → Project** → GitHub에서 리포지토리(`bluemoon_securities`) Import
2. **Root Directory를 `frontend`로 지정 — 모노레포라 이 설정을 빠뜨리면 루트에서 `package.json`을 못 찾아 빌드 실패**
3. **Framework Preset**을 **"Next.js"**로 확인/선택 (자동 감지가 안 되고 "Other"로 남아있는 경우가 있으니 꼭 확인)
4. **Environment Variables** 추가:
   ```
   NEXT_PUBLIC_API_BASE_URL = http://<백엔드 VM Public IP>
   ```
   (백엔드가 아직 준비 안 됐으면 임시값 넣고 나중에 Settings → Environment Variables에서 수정 후 재배포)
5. **Deploy**

이후 `main` 브랜치에 push할 때마다 Vercel이 자동으로 재배포함.

---

## 3. 배포 중 겪은 에러와 해결

### 3-1. `npm error ERESOLVE could not resolve` (eslint 피어 의존성 충돌)
```
npm error peer eslint@">=9.0.0" from eslint-config-next@16.3.4
```
- **원인**: `package.json`에 `eslint`가 `8.57.0`으로 고정되어 있었는데, `eslint-config-next@16.3.4`(Next.js 16용)는 `eslint>=9.0.0`을 요구함. 로컬은 기존 `package-lock.json`이 있어 문제없이 돌아갔지만, Vercel은 캐시 없이 새로 설치하면서 충돌이 그대로 드러남
- **해결**:
  ```bash
  cd frontend
  # package.json의 devDependencies에서
  # "eslint": "8.57.0" → "eslint": "^9.0.0" 으로 수정
  rm -f package-lock.json
  npm install      # lockfile 재생성, 충돌 없이 설치되는지 확인
  npm run build    # 로컬에서 빌드 통과 확인
  ```
  이후 `package.json`/`package-lock.json` 변경분을 커밋 & push하면 Vercel 빌드 통과

> 부가로 발견한 이슈(배포 블로커는 아님): `npm run lint`(`next lint`)가 Next.js 16에서 서브커맨드 자체가 제거되어 깨져 있음(`Invalid project directory provided, no such directory: .../lint`). lint를 다시 쓰려면 `eslint.config.mjs`(flat config)로 마이그레이션하고 `eslint .`를 직접 호출하는 방식으로 바꿔야 함 — 아직 미해결.

### 3-2. `Error: No Output Directory named "public" found after the Build completed`
- **원인**: 빌드 자체(`next build`)는 정상 완료됐는데도 발생 — Vercel 프로젝트 설정의 **Framework Preset이 "Next.js"가 아니라 "Other"(정적 사이트)로 잡혀 있어서**, Vercel이 `.next` 대신 `public` 폴더에서 결과물을 찾으려고 하면서 발생
- **해결**:
  1. Vercel 프로젝트 → **Settings → General → Build & Development Settings**
  2. **Framework Preset**을 **"Next.js"**로 변경
  3. **Output Directory**를 수동으로 `public` 등으로 override 해둔 값이 있으면 지우고 기본값(자동 감지)으로 되돌림
  4. **Deployments → 최근 배포 → ⋯ → Redeploy**

---

## 4. ⚠️ 남은 과제 — HTTPS 프론트 + HTTP 백엔드 Mixed Content 문제

Vercel은 무조건 HTTPS로 서비스되는데, 백엔드는 현재(`오라클_클라우드_배포_가이드.md` 4-10 기준) `http://<Public_IP>`로 HTTPS가 없는 상태.

**브라우저는 HTTPS 페이지에서 HTTP로 가는 fetch/XHR 요청을 기본적으로 차단(mixed content)** 하므로, 백엔드에 HTTPS를 붙이기 전까지는 Vercel에 배포된 프론트에서 로그인/API 호출이 실패할 가능성이 높음.

**해결 방향 (둘 중 하나, 나중에 실제 연동 테스트 시 처리)**:
1. `오라클_클라우드_배포_가이드.md` 4-11(certbot/Let's Encrypt)로 백엔드에 도메인 + HTTPS 붙이기
2. 또는 `frontend/next.config.mjs`에 `rewrites()`로 백엔드를 프록시해서, 브라우저는 Vercel(HTTPS)에만 요청하고 서버 사이드에서 백엔드(HTTP)로 중계

---

## 5. 배포 후 체크리스트

- [ ] Vercel이 준 `https://프로젝트명.vercel.app` URL 접속, 페이지 렌더링 확인
- [ ] 로컬에서 `npm run build` 미리 돌려서 타입 에러 사전 확인 (Vercel 빌드도 동일하게 실패하므로)
- [ ] 백엔드 URL이 정해지면 `NEXT_PUBLIC_API_BASE_URL` 환경변수 업데이트 후 재배포
- [ ] 백엔드 HTTPS 적용 여부에 따라 Mixed Content 이슈(4번) 해결
