import HistoryFilter from "@/components/HistoryFilter";
import { apiGet } from "@/lib/api";
import type { Holding, OrderHistoryItem, Page } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function HistoryPage({ searchParams }: { searchParams: { code?: string; page?: string } }) {
  const code = searchParams.code;
  const pageNum = Number(searchParams.page ?? "0");

  const query = new URLSearchParams({ page: String(pageNum), size: "20" });
  if (code) query.set("code", code);

  let holdings: Holding[] = [];
  let orders: Page<OrderHistoryItem> | null = null;
  try {
    holdings = (await apiGet<Holding[]>("/api/holdings")) ?? [];
  } catch {
    holdings = [];
  }
  try {
    orders = await apiGet<Page<OrderHistoryItem>>(`/api/orders?${query.toString()}`);
  } catch {
    orders = null;
  }

  const rows = orders?.content ?? [];

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>거래 내역</h1>
          <p>전체 매매 기록을 확인하세요</p>
        </div>
      </div>

      <HistoryFilter holdings={holdings} current={code ?? "all"} />

      <div className="card">
        {rows.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>일시</th>
                <th>종목</th>
                <th>구분</th>
                <th>수량</th>
                <th>체결가</th>
                <th>금액</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => (
                <tr key={t.orderId}>
                  <td className="mono">{t.orderedAt.replace("T", " ").slice(0, 16)}</td>
                  <td style={{ textAlign: "left", fontFamily: "var(--font-inter)" }}>{t.stockName}</td>
                  <td className={t.side === "BUY" ? "up" : "down"}>{t.side === "BUY" ? "매수" : "매도"}</td>
                  <td>{t.quantity}</td>
                  <td>{t.price.toLocaleString()}</td>
                  <td>{(t.quantity * t.price).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty-state">
            <div className="icon">📭</div>
            거래 내역이 없습니다
          </div>
        )}
      </div>
    </section>
  );
}
