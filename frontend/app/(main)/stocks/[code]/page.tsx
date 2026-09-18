import Link from "next/link";
import { notFound } from "next/navigation";
import PriceChart from "@/components/PriceChart";
import StockPriceCard from "@/components/StockPriceCard";
import AiInsightCard from "@/components/AiInsightCard";
import NewsSourcesCard from "@/components/NewsSourcesCard";
import WatchlistToggleButton from "@/components/WatchlistToggleButton";
import { apiGet, ApiError } from "@/lib/api";
import { prevTradingDayLabel } from "@/lib/date";
import { fmtPct } from "@/lib/format";
import type { Holding, Insight, IntradayPricePoint, PricePoint, StockDetail, Trade } from "@/lib/types";

export const dynamic = "force-dynamic";

const PERIOD_DAYS: Record<string, number> = {
  "1w": 7,
  "1m": 30,
  "3m": 90,
  "1y": 365,
};
const VALID_PERIODS = new Set(["1d", ...Object.keys(PERIOD_DAYS)]);

async function safeGet<T>(path: string): Promise<T | null> {
  try {
    return await apiGet<T>(path);
  } catch {
    return null;
  }
}

/** "HHMMSS" -> "HH:MM" */
function formatIntradayLabel(time: string): string {
  return time.length >= 4 ? `${time.slice(0, 2)}:${time.slice(2, 4)}` : time;
}

export default async function StockDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ code: string }>;
  searchParams: Promise<{ period?: string }>;
}) {
  const { code } = await params;
  const resolvedSearchParams = await searchParams;
  const period = VALID_PERIODS.has(resolvedSearchParams.period ?? "") ? resolvedSearchParams.period! : "1m";
  const isIntraday = period === "1d";

  let stock: StockDetail | null;
  try {
    stock = await apiGet<StockDetail>(`/api/stocks/${code}`);
  } catch (e) {
    if (e instanceof ApiError && e.code === "STOCK_NOT_FOUND") {
      notFound();
    }
    stock = null;
  }
  if (!stock) notFound();

  const [priceHistoryRaw, intradayRaw, trades, insight, watchlist, holdings] = await Promise.all([
    isIntraday ? Promise.resolve(null) : safeGet<PricePoint[]>(`/api/stocks/${code}/price-history?days=${PERIOD_DAYS[period]}`),
    isIntraday ? safeGet<IntradayPricePoint[]>(`/api/stocks/${code}/intraday`) : Promise.resolve(null),
    safeGet<Trade[]>(`/api/trades/${code}`),
    safeGet<Insight>(`/api/insights/${code}`),
    safeGet<StockDetail[]>("/api/watchlist"),
    safeGet<Holding[]>("/api/holdings"),
  ]);

  const priceHistory: PricePoint[] = isIntraday
    ? (intradayRaw ?? []).map((p) => ({ date: formatIntradayLabel(p.time), price: p.price }))
    : (priceHistoryRaw ?? []).map((p) => ({ date: p.date.slice(5), price: p.price }));

  const isWatched = (watchlist ?? []).some((w) => w.code === code);
  const myHolding = (holdings ?? []).find((h) => h.stockCode === code);

  const referencePrice = isIntraday ? stock.prevClose : myHolding?.avgPrice;
  const referenceLabel = isIntraday ? `${prevTradingDayLabel()} 종가` : "내 평단가";

  const diff = stock.currentPrice - stock.prevClose;
  const rate = stock.prevClose !== 0 ? (diff / stock.prevClose) * 100 : 0;
  const up = diff >= 0;

  return (
    <section>
      <Link href="/stocks" style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 12.5, color: "var(--text-dim)", marginBottom: 12 }}>
        ← 목록으로
      </Link>

      <div className="page-head">
        <div>
          <h1>종목 상세</h1>
          <p>시세와 AI 인사이트를 확인하세요</p>
        </div>
      </div>

      <div className="detail-head">
        <div>
          <div style={{ fontSize: 13, color: "var(--text-dim)", marginBottom: 4 }}>
            {stock.name} · {stock.code}
          </div>
          <div className="detail-price mono">{stock.currentPrice.toLocaleString()}원</div>
          <div className={`summary-delta ${up ? "up" : "down"}`}>
            {up ? "▲" : "▼"} {Math.abs(diff).toLocaleString()}원 ({fmtPct(rate)})
          </div>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <WatchlistToggleButton code={code} initialWatched={isWatched} />
          <Link className="btn btn-buy" href={`/trade?code=${code}&side=BUY`}>
            매수
          </Link>
          <Link className="btn btn-sell" href={`/trade?code=${code}&side=SELL`}>
            매도
          </Link>
        </div>
      </div>

      <div className="grid grid-2" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-head">
            <h3>시세 추이</h3>
          </div>
          <StockPriceCard current={period}>
            <PriceChart data={priceHistory} up={up} referencePrice={referencePrice} referenceLabel={referenceLabel} />
          </StockPriceCard>
        </div>

        <AiInsightCard insight={insight} showNews={false} />
      </div>

      <div style={{ marginBottom: 16 }}>
        <NewsSourcesCard insight={insight} />
      </div>

      <div className="card">
        <div className="card-head">
          <h3>내 매매 이력 (이 종목)</h3>
        </div>
        {trades && trades.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>일시</th>
                <th>구분</th>
                <th>수량</th>
                <th>체결가</th>
                <th>금액</th>
              </tr>
            </thead>
            <tbody>
              {trades.map((t) => (
                <tr key={t.orderId}>
                  <td className="mono">{t.orderedAt.replace("T", " ").slice(0, 16)}</td>
                  <td className={t.side === "BUY" ? "up" : "down"}>{t.side === "BUY" ? "매수" : "매도"}</td>
                  <td>{t.quantity}</td>
                  <td>{t.price.toLocaleString()}</td>
                  <td>{(t.quantity * t.price).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty-state">아직 이 종목의 매매 기록이 없습니다</div>
        )}
      </div>
    </section>
  );
}
