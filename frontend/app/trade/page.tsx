import TradeForm from "@/components/TradeForm";
import { apiGet } from "@/lib/api";
import type { Holding, OrderSide, StockDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function TradePage({ searchParams }: { searchParams: { code?: string; side?: string } }) {
  let allStocks: StockDetail[] = [];
  let holdings: Holding[] = [];
  try {
    [allStocks, holdings] = await Promise.all([
      apiGet<StockDetail[]>("/api/stocks").then((r) => r ?? []),
      apiGet<Holding[]>("/api/holdings").then((r) => r ?? []),
    ]);
  } catch {
    allStocks = [];
    holdings = [];
  }

  const initialSide: OrderSide = searchParams.side === "SELL" ? "SELL" : "BUY";
  const candidateCodes = initialSide === "BUY" ? allStocks.map((s) => s.code) : holdings.map((h) => h.stockCode);
  const initialCode = searchParams.code && candidateCodes.includes(searchParams.code) ? searchParams.code : candidateCodes[0] ?? "";

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>매수 / 매도</h1>
          <p>모의투자 계좌로 안전하게 연습하세요</p>
        </div>
      </div>
      {allStocks.length > 0 ? (
        <TradeForm allStocks={allStocks} holdings={holdings} initialCode={initialCode} initialSide={initialSide} />
      ) : (
        <div className="empty-state">거래 가능한 종목이 없습니다.</div>
      )}
    </section>
  );
}
