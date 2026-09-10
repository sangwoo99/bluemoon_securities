import TradeForm from "@/components/TradeForm";
import { apiGet } from "@/lib/api";
import type { Holding, OrderSide } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function TradePage({ searchParams }: { searchParams: { code?: string; side?: string } }) {
  let holdings: Holding[] = [];
  try {
    holdings = (await apiGet<Holding[]>("/api/holdings")) ?? [];
  } catch {
    holdings = [];
  }

  const initialCode = searchParams.code && holdings.some((h) => h.stockCode === searchParams.code) ? searchParams.code : holdings[0]?.stockCode ?? "";
  const initialSide: OrderSide = searchParams.side === "SELL" ? "SELL" : "BUY";

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>매수 / 매도</h1>
          <p>모의투자 계좌로 안전하게 연습하세요</p>
        </div>
      </div>
      {holdings.length > 0 ? (
        <TradeForm holdings={holdings} initialCode={initialCode} initialSide={initialSide} />
      ) : (
        <div className="empty-state">거래 가능한 종목이 없습니다.</div>
      )}
    </section>
  );
}
