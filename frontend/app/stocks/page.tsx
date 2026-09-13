import Link from "next/link";
import RankFilter from "@/components/RankFilter";
import { apiGet } from "@/lib/api";
import { fmtSignedWon, fmtPct, gainClass, gainArrow } from "@/lib/format";
import type { Holding, StockDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

const RANK_TYPE_MAP: Record<string, string> = {
  fluctuation: "FLUCTUATION",
  volume: "VOLUME",
};

const PAGE_SIZE = 10;

export default async function StocksIndexPage({ searchParams }: { searchParams: Promise<{ rank?: string; page?: string }> }) {
  const resolvedSearchParams = await searchParams;
  const rank = resolvedSearchParams.rank === "volume" ? "volume" : resolvedSearchParams.rank === "fluctuation" ? "fluctuation" : "all";
  const rankType = RANK_TYPE_MAP[rank];
  const page = Math.max(1, parseInt(resolvedSearchParams.page ?? "1", 10) || 1);

  let allStocks: StockDetail[] = [];
  let topMovers: StockDetail[] = [];
  let holdings: Holding[] = [];
  let watchlist: StockDetail[] = [];
  try {
    allStocks = (await apiGet<StockDetail[]>("/api/stocks")) ?? [];
  } catch {
    allStocks = [];
  }
  if (rankType) {
    try {
      topMovers = (await apiGet<StockDetail[]>(`/api/stocks/top-movers?type=${rankType}`)) ?? [];
    } catch {
      topMovers = [];
    }
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

  // 선택한 순위(상승률/거래량) TOP 10을 우선 노출하고, 순위에 없어도 내 보유/관심 종목은 항상 함께 보여준다.
  const allStocksByCode = new Map(allStocks.map((s) => [s.code, s]));
  const topMoverCodes = new Set(topMovers.map((s) => s.code));
  const alwaysShowExtras = [...heldCodes, ...watchedCodes]
    .filter((code) => !topMoverCodes.has(code))
    .filter((code, index, all) => all.indexOf(code) === index)
    .map((code) => allStocksByCode.get(code))
    .filter((s): s is StockDetail => !!s);
  const sortedAllStocks = [...allStocks].sort((a, b) => a.name.localeCompare(b.name, "ko"));
  const totalPages = Math.max(1, Math.ceil(sortedAllStocks.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const displayStocks =
    rank === "all" ? sortedAllStocks.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE) : [...topMovers, ...alwaysShowExtras];

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>종목 목록</h1>
          <p>오늘의 상승률·거래량 상위 종목, 전체 종목, 내 보유·관심 종목을 확인하세요</p>
        </div>
      </div>

      <RankFilter current={rank} />

      {displayStocks.length > 0 ? (
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
              {displayStocks.map((s) => {
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

          {rank === "all" && totalPages > 1 && (
            <div className="pagination">
              <Link
                href={`/stocks?rank=all&page=${Math.max(1, currentPage - 1)}`}
                className={`chip${currentPage === 1 ? " disabled" : ""}`}
                aria-disabled={currentPage === 1}
              >
                ← 이전
              </Link>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
                <Link key={p} href={`/stocks?rank=all&page=${p}`} className={`chip${p === currentPage ? " active" : ""}`}>
                  {p}
                </Link>
              ))}
              <Link
                href={`/stocks?rank=all&page=${Math.min(totalPages, currentPage + 1)}`}
                className={`chip${currentPage === totalPages ? " disabled" : ""}`}
                aria-disabled={currentPage === totalPages}
              >
                다음 →
              </Link>
            </div>
          )}
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
