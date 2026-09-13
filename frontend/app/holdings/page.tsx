import Link from "next/link";
import HoldingsTable from "@/components/HoldingsTable";
import { apiGet } from "@/lib/api";
import { fmtSignedWon, fmtPct, gainClass, gainArrow } from "@/lib/format";
import type { Holding, StockDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function HoldingsPage() {
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

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>보유·관심 종목</h1>
          <p>보유 종목의 손익과 관심 등록한 종목의 시세를 확인하세요</p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head">
          <h3>보유 종목</h3>
        </div>
        <HoldingsTable holdings={holdings} variant="full" />
      </div>

      <div className="card">
        <div className="card-head">
          <h3>관심 종목</h3>
        </div>
        {watchlist.length > 0 ? (
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
              {watchlist.map((s) => {
                const diff = s.currentPrice - s.prevClose;
                const rate = s.prevClose !== 0 ? (diff / s.prevClose) * 100 : 0;
                return (
                  <tr key={s.code} className="clickable">
                    <td>
                      <Link href={`/stocks/${s.code}`} className="stock-name-cell">
                        {s.name}
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
        ) : (
          <div className="empty-state">
            <div className="icon">☆</div>
            아직 관심 등록한 종목이 없습니다 — <Link href="/stocks">종목 목록에서 추가하기</Link>
          </div>
        )}
      </div>
    </section>
  );
}
