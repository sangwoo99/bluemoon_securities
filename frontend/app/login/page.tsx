"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { apiPostClient } from "@/lib/api-client";
import { ApiError } from "@/lib/api-error";
import type { LoginRequest, LoginResponse, SignupRequest, SignupResponse } from "@/lib/types";

type Mode = "login" | "signup";

const ACCESS_TOKEN_MAX_AGE_SECONDS = 60 * 60 * 24; // 쿠키 보관 기간(1일). 실제 만료는 JWT 자체 유효기간(서버 설정)을 따름.

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function switchMode(next: Mode) {
    setMode(next);
    setErrorMessage(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    try {
      if (mode === "signup") {
        await apiPostClient<SignupRequest, SignupResponse>("/api/auth/signup", { email, password, name });
      }
      const loginResult = await apiPostClient<LoginRequest, LoginResponse>("/api/auth/login", { email, password });
      document.cookie = `accessToken=${loginResult.accessToken}; path=/; max-age=${ACCESS_TOKEN_MAX_AGE_SECONDS}`;
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      setErrorMessage(err instanceof ApiError ? err.message : "처리 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="card auth-card">
        <div style={{ textAlign: "center", marginBottom: 22 }}>
          <div className="brand" style={{ fontSize: 26 }}>
            블루문<span>.</span>
          </div>
          <div className="brand-sub" style={{ padding: 0, marginTop: 4 }}>
            근거 있는 투자 기록
          </div>
        </div>

        <div className="auth-tabs">
          <button type="button" className={`auth-tab${mode === "login" ? " active" : ""}`} onClick={() => switchMode("login")}>
            로그인
          </button>
          <button type="button" className={`auth-tab${mode === "signup" ? " active" : ""}`} onClick={() => switchMode("signup")}>
            회원가입
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          {mode === "signup" && (
            <div className="form-row">
              <label htmlFor="authName">이름</label>
              <input id="authName" type="text" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>
          )}

          <div className="form-row">
            <label htmlFor="authEmail">이메일</label>
            <input
              id="authEmail"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </div>

          <div className="form-row">
            <label htmlFor="authPassword">비밀번호</label>
            <input
              id="authPassword"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={mode === "signup" ? 8 : undefined}
              required
            />
            {mode === "signup" && (
              <div style={{ fontSize: 11, color: "var(--text-faint)", marginTop: 6 }}>8자 이상 입력해주세요.</div>
            )}
          </div>

          {errorMessage && <div className="form-error" style={{ marginBottom: 14 }}>{errorMessage}</div>}

          <button className="btn btn-primary" style={{ width: "100%" }} disabled={submitting} type="submit">
            {submitting ? "처리 중..." : mode === "login" ? "로그인" : "가입하고 시작하기"}
          </button>
        </form>

        {mode === "signup" && (
          <div style={{ fontSize: 11.5, color: "var(--text-faint)", marginTop: 16, lineHeight: 1.6 }}>
            가입 시 모의투자 계좌가 자동 생성되고, 시드머니 10,000,000원이 지급됩니다.
          </div>
        )}
      </div>
    </div>
  );
}
