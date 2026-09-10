import Link from "next/link";
import type { Holding } from "@/lib/types";
import { fmtSignedWon, fmtPct, gainClass } from "@/lib/format";

export default function HoldingsTable({ holdings, variant = "full" }: { holdings: Holding[]; variant?: "full" | "compact" }) {
  if (holdings.length === 0) {
    return (
      <div className="empty-state">
        <div className="icon">📭</div>
        아직 보유한 종목이 없습니다 — <Link href="/trade">매수하러 가기</Link>
      </div>
    );
  }

  if (variant === "compact") {
    return (
      <table>
        <thead>
          <tr>
            <th>종목</th>
            <th>현재가</th>
            <th>평가손익</th>
            <th>수익률</th>
          </tr>
        </thead>
        <tbody>
          {holdings.map((h) => (
            <tr key={h.stockCode} className="clickable">
              <td>
                <Link href={`/stocks/${h.stockCode}`} className="stock-name-cell">
                  {h.stockName}
                  <span className="stock-code">{h.stockCode}</span>
                </Link>
              </td>
              <td>{h.currentPrice.toLocaleString()}</td>
              <td className={gainClass(h.evalGain)}>{fmtSignedWon(h.evalGain)}</td>
              <td className={gainClass(h.evalGainRate)}>{fmtPct(h.evalGainRate)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }

  return (
    <table>
      <thead>
        <tr>
          <th>종목</th>
          <th>보유수량</th>
          <th>평단가</th>
          <th>현재가</th>
          <th>평가금액</th>
          <th>평가손익</th>
          <th>수익률</th>
        </tr>
      </thead>
      <tbody>
        {holdings.map((h) => (
          <tr key={h.stockCode} className="clickable">
            <td>
              <Link href={`/stocks/${h.stockCode}`} className="stock-name-cell">
                {h.stockName}
                <span className="stock-code">{h.stockCode}</span>
              </Link>
            </td>
            <td>{h.quantity.toLocaleString()}주</td>
            <td>{h.avgPrice.toLocaleString()}</td>
            <td>{h.currentPrice.toLocaleString()}</td>
            <td>{h.evalValue.toLocaleString()}</td>
            <td className={gainClass(h.evalGain)}>{fmtSignedWon(h.evalGain)}</td>
            <td className={gainClass(h.evalGainRate)}>{fmtPct(h.evalGainRate)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
