"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { apiPostClient, apiDeleteClient } from "@/lib/api-client";
import { ApiError } from "@/lib/api-error";

export default function WatchlistToggleButton({ code, initialWatched }: { code: string; initialWatched: boolean }) {
  const router = useRouter();
  const [watched, setWatched] = useState(initialWatched);
  const [submitting, setSubmitting] = useState(false);

  async function toggle() {
    setSubmitting(true);
    try {
      if (watched) {
        await apiDeleteClient<void>(`/api/watchlist/${code}`);
      } else {
        await apiPostClient<Record<string, never>, void>(`/api/watchlist/${code}`, {});
      }
      setWatched(!watched);
      router.refresh();
    } catch (e) {
      // 낙관적 갱신 없이 실패 시 조용히 원상태 유지 — 별도 토스트 인프라는 이 프로젝트 스코프 밖
      if (e instanceof ApiError) {
        // eslint-disable-next-line no-console
        console.error(e.message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <button
      type="button"
      className="btn btn-ghost"
      onClick={toggle}
      disabled={submitting}
      aria-pressed={watched}
      style={{ padding: "8px 14px", color: watched ? "var(--accent)" : undefined }}
    >
      {watched ? "★ 관심종목" : "☆ 관심종목 추가"}
    </button>
  );
}
