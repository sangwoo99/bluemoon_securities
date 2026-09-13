"use client";

import { useElementSize } from "@/lib/useElementSize";
import type { Holding } from "@/lib/types";

const COLORS = ["#FFB74A", "#FF5C5C", "#4C8DFF", "#8A92A6", "#3DDC97", "#B98CFF"];

export default function AllocationChart({ holdings }: { holdings: Holding[] }) {
  const { ref, width, height } = useElementSize<HTMLDivElement>();

  if (holdings.length === 0) {
    return <div className="empty-state">표시할 보유 종목이 없습니다</div>;
  }

  // 투자원금(매수 시점 기준 금액) 대비 종목별 비중 — 시세 변동으로 비중이 흔들리지 않도록 평가금액이 아닌 매수 원가를 쓴다.
  // 비중이 큰 종목부터 보이도록 정렬 — 도넛 조각 순서와 범례 순서를 일치시킨다.
  const data = holdings
    .map((h) => ({ name: h.stockName, value: h.quantity * h.avgPrice }))
    .sort((a, b) => b.value - a.value);
  const total = data.reduce((sum, d) => sum + d.value, 0);
  const pctOf = (value: number) => (total > 0 ? Math.round((value / total) * 100) : 0);

  // recharts의 Pie가 이 환경에서 중심좌표를 NaN으로 계산하는 문제가 있어, 도넛 링을 stroke-dasharray로 직접 그린다.
  const size = Math.max(0, Math.min(width, height));
  const strokeWidth = size * 0.22;
  const radius = Math.max(0, size / 2 - strokeWidth / 2 - 3);
  const circumference = 2 * Math.PI * radius;

  let offsetSoFar = 0;
  const segments = data.map((d, i) => {
    const fraction = total > 0 ? d.value / total : 0;
    const segLen = fraction * circumference;
    const seg = { name: d.name, value: d.value, color: COLORS[i % COLORS.length], dasharray: `${segLen} ${circumference - segLen}`, dashoffset: -offsetSoFar };
    offsetSoFar += segLen;
    return seg;
  });

  return (
    <div className="chart-box small allocation" ref={ref}>
      {width > 0 && height > 0 && (
        <>
          <svg width={size} height={size}>
            <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
              {segments.map((s, i) => (
                <circle
                  key={i}
                  cx={size / 2}
                  cy={size / 2}
                  r={radius}
                  fill="none"
                  stroke={s.color}
                  strokeWidth={strokeWidth}
                  strokeDasharray={s.dasharray}
                  strokeDashoffset={s.dashoffset}
                />
              ))}
            </g>
          </svg>
          <div className="allocation-legend">
            {segments.map((s, i) => (
              <div key={i} className="allocation-legend-item" title={`${s.value.toLocaleString()}원 (투자원금)`}>
                <span className="dot" style={{ background: s.color }} />
                {s.name} {pctOf(s.value)}%
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
