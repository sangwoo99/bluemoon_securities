import { apiGet } from "@/lib/api";
import type { UserProfile } from "@/lib/types";
import ChangePasswordForm from "@/components/ChangePasswordForm";

export const dynamic = "force-dynamic";

export default async function MyPage() {
  let profile: UserProfile | null = null;
  try {
    profile = await apiGet<UserProfile>("/api/users/me");
  } catch {
    profile = null;
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>마이페이지</h1>
          <p>계정 및 모의투자 계좌 정보를 확인하세요</p>
        </div>
      </div>

      {profile ? (
        <div className="card" style={{ maxWidth: 480 }}>
          <table style={{ fontSize: 14 }}>
            <tbody>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none", padding: "12px 0" }}>이름</td>
                <td style={{ textAlign: "right", border: "none", padding: "12px 0" }}>{profile.name}</td>
              </tr>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none", padding: "12px 0" }}>이메일</td>
                <td style={{ textAlign: "right", border: "none", padding: "12px 0" }} className="mono">{profile.email}</td>
              </tr>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none", padding: "12px 0" }}>가입일</td>
                <td style={{ textAlign: "right", border: "none", padding: "12px 0" }} className="mono">{profile.createdAt.replace("T", " ").slice(0, 16)}</td>
              </tr>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none", padding: "12px 0" }}>계좌 개설일</td>
                <td style={{ textAlign: "right", border: "none", padding: "12px 0" }} className="mono">{profile.accountCreatedAt.replace("T", " ").slice(0, 16)}</td>
              </tr>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none", padding: "12px 0" }}>현금 잔고</td>
                <td style={{ textAlign: "right", border: "none", padding: "12px 0", fontSize: 18, fontWeight: 600 }} className="mono">
                  {profile.cashBalance.toLocaleString()}원
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">프로필 정보를 불러올 수 없습니다.</div>
      )}

      <ChangePasswordForm />
    </section>
  );
}
