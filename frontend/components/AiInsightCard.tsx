import Link from "next/link";
import type { Insight } from "@/lib/types";

export default function AiInsightCard({
  insight,
  generatedAtLabel,
  linkStock = false,
  badgeLabel = "AI 인사이트",
  showNews = true,
}: {
  insight: Insight | null;
  generatedAtLabel?: string;
  linkStock?: boolean;
  badgeLabel?: string;
  showNews?: boolean;
}) {
  if (!insight) {
    return (
      <div className="card ai-card">
        <div className="card-head">
          <span className="ai-badge">✦ {badgeLabel}</span>
        </div>
        <p className="ai-reason">최근 뉴스가 없어 인사이트를 불러올 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="card ai-card">
      <div className="card-head">
        <span className="ai-badge">✦ {badgeLabel}</span>
        {generatedAtLabel && <span style={{ fontSize: 11, color: "var(--text-faint)" }}>{generatedAtLabel}</span>}
      </div>

      {insight.stockCode && insight.stockName && (
        <div className="ai-pick">
          <span className="code">{insight.stockCode}</span>
          {linkStock ? (
            <Link href={`/stocks/${insight.stockCode}`} className="name">
              {insight.stockName}
            </Link>
          ) : (
            <span className="name">{insight.stockName}</span>
          )}
        </div>
      )}

      <p className={linkStock ? "ai-reason clamp" : "ai-reason"}>{insight.content}</p>

      {linkStock && insight.stockCode && (
        <Link href={`/stocks/${insight.stockCode}`} className="card-link ai-detail-link">
          자세히 보기 →
        </Link>
      )}

      {showNews && (
        <div className="ai-sources">
          <span style={{ fontSize: 11, color: "var(--text-faint)", marginRight: 4 }}>참고 뉴스</span>
          {insight.sources.map((s, i) => (
            <span className="source-tag" key={i}>
              {s.name} · {s.date}
            </span>
          ))}
        </div>
      )}

      <div className="ai-disclaimer">본 정보는 뉴스·공시 검색 기반 참고용 요약이며, 투자 판단과 책임은 본인에게 있습니다.</div>
    </div>
  );
}
