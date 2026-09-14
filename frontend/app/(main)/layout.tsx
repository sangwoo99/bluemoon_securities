import { cookies } from "next/headers";
import Sidebar from "@/components/Sidebar";
import TickerTape, { type TickerItem } from "@/components/TickerTape";
import { isTokenExpired } from "@/lib/auth";
import { apiGet } from "@/lib/api";
import type { Holding, StockDetail } from "@/lib/types";

function stockRate(s: StockDetail): number {
  return s.prevClose !== 0 ? ((s.currentPrice - s.prevClose) / s.prevClose) * 100 : 0;
}

async function getTopMoverItems(): Promise<TickerItem[]> {
  try {
    const topMovers = (await apiGet<StockDetail[]>("/api/stocks/top-movers?type=VOLUME")) ?? [];
    return topMovers.map((s) => ({ code: s.code, name: s.name, price: s.currentPrice, rate: stockRate(s) }));
  } catch {
    return [];
  }
}

async function safeGet<T>(path: string): Promise<T | null> {
  try {
    return await apiGet<T>(path);
  } catch {
    return null;
  }
}

async function getTickerItems(isAuthenticated: boolean): Promise<TickerItem[]> {
  if (!isAuthenticated) {
    return getTopMoverItems();
  }

  const [topMoverItems, holdingsResult, watchlistResult] = await Promise.all([
    getTopMoverItems(),
    safeGet<Holding[]>("/api/holdings"),
    safeGet<StockDetail[]>("/api/watchlist"),
  ]);
  const holdings = holdingsResult ?? [];
  const watchlist = watchlistResult ?? [];

  const heldCodes = new Set(holdings.map((h) => h.stockCode));
  const holdingItems: TickerItem[] = holdings.map((h) => ({ code: h.stockCode, name: h.stockName, price: h.currentPrice, rate: h.evalGainRate }));
  const watchlistItems: TickerItem[] = watchlist.filter((w) => !heldCodes.has(w.code)).map((w) => ({ code: w.code, name: w.name, price: w.currentPrice, rate: stockRate(w) }));

  const shownCodes = new Set([...heldCodes, ...watchlistItems.map((w) => w.code)]);
  const extraTopMovers = topMoverItems.filter((t) => !shownCodes.has(t.code));

  return [...holdingItems, ...watchlistItems, ...extraTopMovers];
}

export default async function MainLayout({ children }: { children: React.ReactNode }) {
  const token = (await cookies()).get("accessToken")?.value;
  const isAuthenticated = !!token && !isTokenExpired(token);
  const tickerItems = await getTickerItems(isAuthenticated);

  return (
    <>
      <TickerTape items={tickerItems} />
      <div className="shell">
        <Sidebar />
        <main>{children}</main>
      </div>
    </>
  );
}
