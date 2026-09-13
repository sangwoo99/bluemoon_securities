import type { Insight } from "@/lib/types";

export default function NewsSourcesCard({ insight }: { insight: Insight | null }) {
  if (!insight || insight.sources.length === 0) {
    return (
      <div className="card">
        <div className="card-head">
          <h3>관련 뉴스</h3>
        </div>
        <div className="empty-state">관련 뉴스가 없습니다</div>
      </div>
    );
  }

  return (
    <div className="card">
      <div className="card-head">
        <h3>관련 뉴스</h3>
      </div>
      <div className="ai-news-list">
        <ul>
          {insight.sources.map((s, i) => (
            <li key={i}>
              {s.title ? (
                s.url ? (
                  <a href={s.url} target="_blank" rel="noopener noreferrer">
                    {s.title}
                  </a>
                ) : (
                  <span>{s.title}</span>
                )
              ) : null}
              <span className="ai-news-meta">
                {s.title ? " — " : ""}
                {s.name} · {s.date}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
