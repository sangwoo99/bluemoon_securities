"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import ConfirmModal from "./ConfirmModal";
import SearchableSelect from "./SearchableSelect";
import { apiPostClient } from "@/lib/api-client";
import { ApiError } from "@/lib/api-error";
import type { CreateOrderRequest, CreateOrderResponse, Holding, OrderSide, OrderType, StockDetail } from "@/lib/types";

interface TradableStock {
  code: string;
  name: string;
  currentPrice: number;
  quantity: number;
  avgPrice: number;
}

export default function TradeForm({
  allStocks,
  holdings,
  initialCode,
  initialSide,
}: {
  allStocks: StockDetail[];
  holdings: Holding[];
  initialCode: string;
  initialSide: OrderSide;
}) {
  const router = useRouter();
  const [stockCode, setStockCode] = useState(initialCode);
  const [side, setSide] = useState<OrderSide>(initialSide);
  const [orderType, setOrderType] = useState<OrderType>("MARKET");
  const [quantity, setQuantity] = useState("1");
  const [limitPrice, setLimitPrice] = useState("");
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const holdingsByCode = useMemo(() => new Map(holdings.map((h) => [h.stockCode, h])), [holdings]);

  // 매수는 전체 종목 중에서, 매도는 실제 보유한 종목 중에서만 고를 수 있다.
  const buyList: TradableStock[] = useMemo(
    () =>
      allStocks.map((s) => {
        const h = holdingsByCode.get(s.code);
        return { code: s.code, name: s.name, currentPrice: s.currentPrice, quantity: h?.quantity ?? 0, avgPrice: h?.avgPrice ?? 0 };
      }),
    [allStocks, holdingsByCode]
  );
  const sellList: TradableStock[] = useMemo(
    () => holdings.map((h) => ({ code: h.stockCode, name: h.stockName, currentPrice: h.currentPrice, quantity: h.quantity, avgPrice: h.avgPrice })),
    [holdings]
  );
  const list = side === "BUY" ? buyList : sellList;

  function handleSideChange(nextSide: OrderSide) {
    setSide(nextSide);
    const nextList = nextSide === "BUY" ? buyList : sellList;
    if (!nextList.some((s) => s.code === stockCode)) {
      setStockCode(nextList[0]?.code ?? "");
    }
  }

  const selected = list.find((s) => s.code === stockCode) ?? list[0];

  const qtyNum = parseInt(quantity, 10) || 0;
  const limitPriceNum = parseFloat(limitPrice) || 0;
  const unitPrice = orderType === "LIMIT" ? limitPriceNum : selected?.currentPrice ?? 0;
  const estimatedAmount = qtyNum * unitPrice;

  const validationError = useMemo(() => {
    if (!selected) return "선택 가능한 종목이 없습니다.";
    if (qtyNum <= 0) return "수량은 1주 이상 입력해주세요.";
    if (orderType === "LIMIT" && limitPriceNum <= 0) return "지정가를 입력해주세요.";
    if (side === "SELL" && qtyNum > selected.quantity) return "보유 수량을 초과했습니다.";
    return null;
  }, [selected, qtyNum, orderType, limitPriceNum, side]);

  if (!selected) {
    return <div className="empty-state">거래할 수 있는 종목이 없습니다.</div>;
  }

  async function submitOrder() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const payload: CreateOrderRequest = {
        stockCode: selected.code,
        side,
        orderType,
        quantity: qtyNum,
        limitPrice: orderType === "LIMIT" ? limitPriceNum : null,
      };
      const response = await apiPostClient<CreateOrderRequest, CreateOrderResponse>("/api/orders", payload);
      setShowConfirm(false);
      router.push(`/history?justPlaced=${response.status}`);
      router.refresh();
    } catch (e) {
      setErrorMessage(e instanceof ApiError ? e.message : "주문 처리 중 오류가 발생했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <div className="grid grid-2">
        <div className="card">
          <div className="trade-tabs">
            <button className={`trade-tab${side === "BUY" ? " active-buy" : ""}`} onClick={() => handleSideChange("BUY")} type="button">
              매수
            </button>
            <button className={`trade-tab${side === "SELL" ? " active-sell" : ""}`} onClick={() => handleSideChange("SELL")} type="button">
              매도
            </button>
          </div>

          <div className="form-row">
            <label htmlFor="tradeStockSelect">종목</label>
            <SearchableSelect id="tradeStockSelect" options={list} value={stockCode} onChange={setStockCode} />
          </div>

          <div className="form-row">
            <label>주문 방식</label>
            <div className="order-type-toggle">
              <button type="button" className={`chip${orderType === "MARKET" ? " active" : ""}`} onClick={() => setOrderType("MARKET")}>
                시장가
              </button>
              <button type="button" className={`chip${orderType === "LIMIT" ? " active" : ""}`} onClick={() => setOrderType("LIMIT")}>
                지정가
              </button>
            </div>
          </div>

          {orderType === "LIMIT" && (
            <div className="form-row">
              <label htmlFor="limitPriceInput">지정가 (원)</label>
              <input
                id="limitPriceInput"
                type="number"
                placeholder="가격 입력"
                value={limitPrice}
                onChange={(e) => setLimitPrice(e.target.value)}
              />
              <div style={{ fontSize: 11.5, color: "var(--text-faint)", marginTop: 6 }}>
                {side === "BUY"
                  ? "현재가 이하로 내려와야 체결됩니다. 그 전까지는 주문 상태로 대기하며, 대기 중에는 언제든 취소할 수 있습니다."
                  : "현재가 이상으로 올라야 체결됩니다. 그 전까지는 주문 상태로 대기하며, 대기 중에는 언제든 취소할 수 있습니다."}
              </div>
            </div>
          )}

          <div className="form-row">
            <label htmlFor="qtyInput">수량 (주)</label>
            <input id="qtyInput" type="number" min={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} />
            {validationError && <div className="form-error">{validationError}</div>}
          </div>

          <div className="estimate-box">
            <span className="label">예상 체결금액</span>
            <span className="value mono">{estimatedAmount.toLocaleString()}원</span>
          </div>

          <button
            className={`btn ${side === "BUY" ? "btn-buy" : "btn-sell"}`}
            style={{ width: "100%" }}
            disabled={!!validationError}
            onClick={() => setShowConfirm(true)}
          >
            {side === "BUY" ? "매수 주문하기" : "매도 주문하기"}
          </button>
        </div>

        <div className="card">
          <div className="card-head">
            <h3>선택 종목 정보</h3>
          </div>
          <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 4 }}>
            {selected.name} <span className="mono" style={{ fontSize: 12, color: "var(--text-faint)" }}>{selected.code}</span>
          </div>
          <div className="mono" style={{ fontSize: 24, marginBottom: 14 }}>
            {selected.currentPrice.toLocaleString()}원
          </div>
          <table style={{ fontSize: 13 }}>
            <tbody>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none" }}>보유 수량</td>
                <td style={{ border: "none" }}>{selected.quantity}주</td>
              </tr>
              <tr>
                <td style={{ textAlign: "left", color: "var(--text-dim)", fontFamily: "var(--font-inter)", border: "none" }}>평단가</td>
                <td style={{ border: "none" }}>{selected.avgPrice.toLocaleString()}원</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {showConfirm && (
        <ConfirmModal
          stockName={selected.name}
          side={side}
          orderType={orderType}
          quantity={qtyNum}
          estimatedAmount={estimatedAmount}
          submitting={submitting}
          errorMessage={errorMessage}
          onCancel={() => {
            setShowConfirm(false);
            setErrorMessage(null);
          }}
          onConfirm={submitOrder}
        />
      )}
    </>
  );
}
