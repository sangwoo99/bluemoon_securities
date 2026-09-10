import type { PortfolioSummary } from "@/lib/types";
import { fmtSignedWon, fmtPct, fmtWon, gainClass, gainArrow } from "@/lib/format";

export default function SummaryCards({ summary }: { summary: PortfolioSummary }) {
  return (
    <div className="summary-row">
      <div className="card summary-card">
        <div className="summary-label">총 평가금액</div>
        <div className="summary-value mono">{fmtWon(summary.totalValue)}</div>
        <div className={`summary-delta ${gainClass(summary.todayChange)}`}>
          {gainArrow(summary.todayChange)} {Math.abs(summary.todayChange).toLocaleString()}원 ({fmtPct(summary.todayChangeRate)}) 오늘
        </div>
      </div>
      <div className="card summary-card">
        <div className="summary-label">총 손익 (원금 대비)</div>
        <div className={`summary-value mono ${gainClass(summary.totalGain)}`}>{fmtSignedWon(summary.totalGain)}</div>
        <div className={`summary-delta ${gainClass(summary.totalGainRate)}`}>
          {gainArrow(summary.totalGainRate)} {fmtPct(summary.totalGainRate)}
        </div>
      </div>
      <div className="card summary-card">
        <div className="summary-label">투자 원금</div>
        <div className="summary-value mono">{fmtWon(summary.totalCost)}</div>
        <div className="summary-delta" style={{ color: "var(--text-faint)" }}>
          보유 {summary.holdingCount}종목
        </div>
      </div>
    </div>
  );
}
