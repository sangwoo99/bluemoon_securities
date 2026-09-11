import { NextResponse, type NextRequest } from "next/server";

const PUBLIC_PATHS = ["/login"];

/** JWT의 exp claim만 확인 (서명 검증은 백엔드 책임 — 여기서는 만료된 쿠키를 걸러 재로그인시키는 용도). */
function isTokenExpired(token: string): boolean {
  try {
    const payload = token.split(".")[1];
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return typeof decoded.exp !== "number" || Date.now() >= decoded.exp * 1000;
  } catch {
    return true;
  }
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isPublicPath = PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
  const token = request.cookies.get("accessToken")?.value;
  const isAuthenticated = !!token && !isTokenExpired(token);

  if (!isAuthenticated && !isPublicPath) {
    const response = NextResponse.redirect(new URL("/login", request.url));
    if (token) response.cookies.delete("accessToken");
    return response;
  }

  if (isAuthenticated && isPublicPath) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
