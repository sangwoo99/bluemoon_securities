"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { apiDeleteClient } from "@/lib/api-client";
import { ApiError } from "@/lib/api-error";
import type { CancelOrderResponse } from "@/lib/types";

export default function CancelOrderButton({ orderId, stockName }: { orderId: number; stockName: string }) {
  const router = useRouter();
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleConfirm() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await apiDeleteClient<CancelOrderResponse>(`/api/orders/${orderId}`);
      setShowConfirm(false);
      router.refresh();
    } catch (e) {
      setErrorMessage(e instanceof ApiError ? e.message : "취소 처리 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className="btn btn-ghost"
        style={{ padding: "5px 12px", fontSize: 12 }}
        onClick={() => setShowConfirm(true)}
      >
        취소
      </button>

      {showConfirm && (
        <div className="modal-overlay">
          <div className="modal">
            <h3>주문 취소</h3>
            <div className="modal-row">
              <span>종목</span>
              <span>{stockName}</span>
            </div>
            <p style={{ fontSize: 12.5, color: "var(--text-dim)", marginTop: 12, lineHeight: 1.6 }}>
              아직 체결되지 않은 주문입니다. 취소하면 대기가 종료되며, 체결 전이라 현금/보유수량은 변동되지 않습니다.
            </p>
            {errorMessage && <div className="modal-error">{errorMessage}</div>}
            <div className="modal-actions">
              <button className="btn btn-ghost" onClick={() => setShowConfirm(false)} disabled={submitting}>
                닫기
              </button>
              <button className="btn btn-sell" onClick={handleConfirm} disabled={submitting}>
                {submitting ? "처리 중..." : "주문 취소"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
