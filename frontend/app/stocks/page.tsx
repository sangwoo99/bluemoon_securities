import Link from "next/link";
import { apiGet } from "@/lib/api";
import { fmtSignedWon, fmtPct, gainClass, gainArrow } from "@/lib/format";
import type { Holding, StockDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function StocksIndexPage() {
  let allStocks: StockDetail[] = [];
  let holdings: Holding[] = [];
  let watchlist: StockDetail[] = [];
  try {
    allStocks = (await apiGet<StockDetail[]>("/api/stocks")) ?? [];
  } catch {
    allStocks = [];
  }
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
  const watchedCodes = new Set(watchlist.map((w) => w.code));

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>종목 상세</h1>
          <p>종목을 선택하면 시세와 AI 인사이트를 확인할 수 있어요</p>
        </div>
      </div>

      {allStocks.length > 0 ? (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>종목</th>
                <th>시장</th>
                <th>현재가</th>
                <th>전일대비</th>
              </tr>
            </thead>
            <tbody>
              {allStocks.map((s) => {
                const diff = s.currentPrice - s.prevClose;
                const rate = s.prevClose !== 0 ? (diff / s.prevClose) * 100 : 0;
                return (
                  <tr key={s.code} className="clickable">
                    <td>
                      <Link href={`/stocks/${s.code}`} className="stock-name-cell">
                        <span>
                          {s.name}
                          {heldCodes.has(s.code) && <span className="source-tag" style={{ marginLeft: 6 }}>보유중</span>}
                          {watchedCodes.has(s.code) && <span style={{ marginLeft: 6, color: "var(--accent)" }}>★</span>}
                        </span>
                        <span className="stock-code">{s.code}</span>
                      </Link>
                    </td>
                    <td>{s.market}</td>
                    <td>{s.currentPrice.toLocaleString()}원</td>
                    <td className={gainClass(diff)}>
                      {gainArrow(diff)} {fmtSignedWon(diff)} ({fmtPct(rate)})
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">
          <div className="icon">📭</div>
          불러올 수 있는 종목이 없습니다
        </div>
      )}
    </section>
  );
}
