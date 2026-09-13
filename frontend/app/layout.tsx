import type { Metadata } from "next";
import { cookies } from "next/headers";
import { Space_Grotesk, Inter, IBM_Plex_Mono } from "next/font/google";
import Sidebar from "@/components/Sidebar";
import TickerTape, { type TickerItem } from "@/components/TickerTape";
import { isTokenExpired } from "@/lib/auth";
import { apiGet } from "@/lib/api";
import type { Holding, StockDetail } from "@/lib/types";
import "./globals.css";

const spaceGrotesk = Space_Grotesk({ subsets: ["latin"], weight: ["500", "600", "700"], variable: "--font-space-grotesk" });
const inter = Inter({ subsets: ["latin"], weight: ["400", "500", "600", "700"], variable: "--font-inter" });
const plexMono = IBM_Plex_Mono({ subsets: ["latin"], weight: ["400", "500", "600"], variable: "--font-plex-mono" });

export const metadata: Metadata = {
  title: "블루문 — 투자 포트폴리오",
  description: "RAG 인사이트 기반 모의투자 포트폴리오 트래커",
};

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

async function getTickerItems(isAuthenticated: boolean): Promise<TickerItem[]> {
  const topMoverItems = await getTopMoverItems();
  if (!isAuthenticated) {
    return topMoverItems;
  }

  let holdings: Holding[] = [];
  let watchlist: StockDetail[] = [];
  try {
    holdings = (await apiGet<Holding[]>("/api/holdings")) ?? [];
  } catch {
    holdings = [];
  }
  try {
    watchlist = (await apiGet<StockDetail[]>("/api/watchlist")) ?? [];
  } catch {
    watchlist = [];
  }

  const heldCodes = new Set(holdings.map((h) => h.stockCode));
  const holdingItems: TickerItem[] = holdings.map((h) => ({ code: h.stockCode, name: h.stockName, price: h.currentPrice, rate: h.evalGainRate }));
  const watchlistItems: TickerItem[] = watchlist.filter((w) => !heldCodes.has(w.code)).map((w) => ({ code: w.code, name: w.name, price: w.currentPrice, rate: stockRate(w) }));

  const shownCodes = new Set([...heldCodes, ...watchlistItems.map((w) => w.code)]);
  const extraTopMovers = topMoverItems.filter((t) => !shownCodes.has(t.code));

  return [...holdingItems, ...watchlistItems, ...extraTopMovers];
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const token = (await cookies()).get("accessToken")?.value;
  const isAuthenticated = !!token && !isTokenExpired(token);
  const tickerItems = await getTickerItems(isAuthenticated);

  return (
    <html lang="ko" className={`${spaceGrotesk.variable} ${inter.variable} ${plexMono.variable}`}>
      <body>
        <TickerTape items={tickerItems} />
        <div className="shell">
          <Sidebar />
          <main>{children}</main>
        </div>
      </body>
    </html>
  );
}
