/** JWT의 exp claim만 확인 (서명 검증은 백엔드 책임 — 여기서는 만료된 쿠키를 걸러내는 용도). */
export function isTokenExpired(token: string): boolean {
  try {
    const payload = token.split(".")[1];
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return typeof decoded.exp !== "number" || Date.now() >= decoded.exp * 1000;
  } catch {
    return true;
  }
}
