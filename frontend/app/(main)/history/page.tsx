import HistoryFilter from "@/components/HistoryFilter";
import CancelOrderButton from "@/components/CancelOrderButton";
import { apiGet } from "@/lib/api";
import type { Holding, OrderHistoryItem, OrderStatus, Page } from "@/lib/types";

export const dynamic = "force-dynamic";

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: "주문",
  FILLED: "체결",
  CANCELLED: "취소",
};

const STATUS_PILL_CLASS: Record<OrderStatus, string> = {
  PENDING: "pending",
  FILLED: "up",
  CANCELLED: "muted",
};

export default async function HistoryPage({ searchParams }: { searchParams: Promise<{ code?: string; page?: string; justPlaced?: string }> }) {
  const resolvedSearchParams = await searchParams;
  const code = resolvedSearchParams.code;
  const pageNum = Number(resolvedSearchParams.page ?? "0");
  const justPlaced = resolvedSearchParams.justPlaced === "PENDING" || resolvedSearchParams.justPlaced === "FILLED" ? resolvedSearchParams.justPlaced : null;

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

      {justPlaced === "PENDING" && (
        <div className="notice-banner pending">
          지정가 주문이 접수되었습니다. 현재가가 지정가 조건을 만족하면 체결됩니다 — 체결 전까지는 아래에서 언제든 취소할 수 있습니다.
        </div>
      )}
      {justPlaced === "FILLED" && <div className="notice-banner filled">주문이 체결되었습니다.</div>}

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
                <th>가격</th>
                <th>금액</th>
                <th>상태</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t) => (
                // orderId는 FILLED면 ORDERS.id, PENDING/CANCELLED면 PENDING_ORDERS.id를 가리키는
                // 서로 다른(독립된) 시퀀스라 숫자가 겹칠 수 있어 상태를 함께 key에 포함한다.
                <tr key={`${t.status === "FILLED" ? "order" : "pending"}-${t.orderId}`}>
                  <td className="mono">{t.orderedAt.replace("T", " ").slice(0, 16)}</td>
                  <td style={{ textAlign: "left", fontFamily: "var(--font-inter)" }}>{t.stockName}</td>
                  <td className={t.side === "BUY" ? "up" : "down"}>{t.side === "BUY" ? "매수" : "매도"}</td>
                  <td>{t.quantity}</td>
                  <td>{t.price.toLocaleString()}</td>
                  <td>{(t.quantity * t.price).toLocaleString()}</td>
                  <td>
                    <span className={`pill ${STATUS_PILL_CLASS[t.status]}`}>{STATUS_LABEL[t.status]}</span>
                  </td>
                  <td>{t.cancelable && <CancelOrderButton orderId={t.orderId} stockName={t.stockName} />}</td>
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
