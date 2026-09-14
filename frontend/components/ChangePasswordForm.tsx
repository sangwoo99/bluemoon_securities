"use client";

import { useState, type FormEvent } from "react";
import { apiPutClient } from "@/lib/api-client";
import { ApiError } from "@/lib/api-error";

export default function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (newPassword !== confirmPassword) {
      setErrorMessage("새 비밀번호가 일치하지 않습니다.");
      return;
    }

    setSubmitting(true);
    try {
      await apiPutClient<{ currentPassword: string; newPassword: string }, void>("/api/users/me/password", {
        currentPassword,
        newPassword,
      });
      setSuccessMessage("비밀번호가 변경되었습니다.");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      setErrorMessage(err instanceof ApiError ? err.message : "처리 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card" style={{ maxWidth: 480, marginTop: 16 }}>
      <div className="card-head">
        <h3>비밀번호 변경</h3>
      </div>
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <label htmlFor="currentPassword">현재 비밀번호</label>
          <input
            id="currentPassword"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
        </div>
        <div className="form-row">
          <label htmlFor="newPassword">새 비밀번호</label>
          <input
            id="newPassword"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            minLength={8}
            required
          />
          <div style={{ fontSize: 11, color: "var(--text-faint)", marginTop: 6 }}>8자 이상 입력해주세요.</div>
        </div>
        <div className="form-row">
          <label htmlFor="confirmPassword">새 비밀번호 확인</label>
          <input
            id="confirmPassword"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            minLength={8}
            required
          />
        </div>

        {errorMessage && <div className="form-error" style={{ marginBottom: 14 }}>{errorMessage}</div>}
        {successMessage && (
          <div style={{ fontSize: 12, color: "var(--accent)", marginBottom: 14 }}>{successMessage}</div>
        )}

        <button className="btn btn-primary" style={{ width: "100%" }} disabled={submitting} type="submit">
          {submitting ? "변경 중..." : "비밀번호 변경"}
        </button>
      </form>
    </div>
  );
}
