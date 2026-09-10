import Link from "next/link";
import type { Insight } from "@/lib/types";

export default function AiInsightCard({
  insight,
  generatedAtLabel,
  linkStock = false,
}: {
  insight: Insight | null;
  generatedAtLabel?: string;
  linkStock?: boolean;
}) {
  if (!insight) {
    return (
      <div className="card ai-card">
        <div className="card-head">
          <span className="ai-badge">✦ AI 인사이트</span>
        </div>
        <p className="ai-reason">일시적으로 인사이트를 불러올 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className="card ai-card">
      <div className="card-head">
        <span className="ai-badge">✦ AI 인사이트</span>
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

      <p className="ai-reason">{insight.content}</p>

      <div className="ai-sources">
        {insight.sources.map((s, i) => (
          <span className="source-tag" key={i}>
            {s.name} · {s.date}
          </span>
        ))}
      </div>

      <div className="ai-disclaimer">본 정보는 뉴스·공시 검색 기반 참고용 요약이며, 투자 판단과 책임은 본인에게 있습니다.</div>
    </div>
  );
}
