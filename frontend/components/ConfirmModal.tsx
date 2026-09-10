"use client";

import type { OrderSide, OrderType } from "@/lib/types";

interface ConfirmModalProps {
  stockName: string;
  side: OrderSide;
  orderType: OrderType;
  quantity: number;
  estimatedAmount: number;
  submitting: boolean;
  errorMessage: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}

export default function ConfirmModal({
  stockName,
  side,
  orderType,
  quantity,
  estimatedAmount,
  submitting,
  errorMessage,
  onCancel,
  onConfirm,
}: ConfirmModalProps) {
  return (
    <div className="modal-overlay">
      <div className="modal">
        <h3>주문 확인</h3>
        <div className="modal-row">
          <span>종목</span>
          <span>{stockName}</span>
        </div>
        <div className="modal-row">
          <span>구분</span>
          <span>{side === "BUY" ? "매수" : "매도"}</span>
        </div>
        <div className="modal-row">
          <span>주문방식</span>
          <span>{orderType === "MARKET" ? "시장가" : "지정가"}</span>
        </div>
        <div className="modal-row">
          <span>수량</span>
          <span>{quantity}주</span>
        </div>
        <div className="modal-row">
          <span>예상 금액</span>
          <span>{estimatedAmount.toLocaleString()}원</span>
        </div>
        {errorMessage && <div className="modal-error">{errorMessage}</div>}
        <div className="modal-actions">
          <button className="btn btn-ghost" onClick={onCancel} disabled={submitting}>
            취소
          </button>
          <button className={`btn ${side === "BUY" ? "btn-buy" : "btn-sell"}`} onClick={onConfirm} disabled={submitting}>
            {submitting ? "처리 중..." : "확인"}
          </button>
        </div>
      </div>
    </div>
  );
}
