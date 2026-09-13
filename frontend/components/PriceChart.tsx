"use client";

import { useElementSize } from "@/lib/useElementSize";
import type { PricePoint } from "@/lib/types";

const PAD_LEFT = 64;
const PAD_RIGHT = 8;
const PAD_TOP = 8;
const PAD_BOTTOM = 4;
const Y_TICKS = 4;

export default function PriceChart({ data, up }: { data: PricePoint[]; up: boolean }) {
  const { ref, width, height } = useElementSize<HTMLDivElement>();

  if (data.length === 0) {
    return <div className="empty-state">표시할 시세 데이터가 없습니다</div>;
  }

  const color = up ? "#FF5C5C" : "#4C8DFF";
  const prices = data.map((d) => d.price);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const range = maxPrice - minPrice;

  const plotWidth = Math.max(0, width - PAD_LEFT - PAD_RIGHT);
  const plotHeight = Math.max(0, height - PAD_TOP - PAD_BOTTOM);

  // recharts의 AreaChart/YAxis가 이 환경에서 스케일 계산이 깨져(값이 낮을수록 위로, 눈금도 한 줄에 겹쳐 그려짐)
  // 그래프 방향이 실제와 반대로 보이는 문제가 있어, 좌표를 직접 계산하는 순수 SVG로 대체한다.
  // range가 0(가격이 하루뿐이거나 전부 동일)이면 바닥에 붙지 않도록 수직 중앙에 그린다.
  const points = data.map((d, i) => ({
    x: PAD_LEFT + (data.length === 1 ? plotWidth / 2 : (i / (data.length - 1)) * plotWidth),
    y: range === 0 ? PAD_TOP + plotHeight / 2 : PAD_TOP + (1 - (d.price - minPrice) / range) * plotHeight,
    price: d.price,
  }));

  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
  const floorY = PAD_TOP + plotHeight;
  const areaPath = `${linePath} L${points[points.length - 1].x.toFixed(1)},${floorY} L${points[0].x.toFixed(1)},${floorY} Z`;

  const yTicks = Array.from({ length: Y_TICKS + 1 }, (_, i) => ({
    value: maxPrice - (i * range) / Y_TICKS,
    y: PAD_TOP + (i / Y_TICKS) * plotHeight,
  }));

  const xTickCount = Math.min(6, data.length);
  const xTicks =
    xTickCount <= 1
      ? [{ label: data[0]?.date.slice(5) ?? "" }]
      : Array.from({ length: xTickCount }, (_, i) => {
          const idx = Math.round((i * (data.length - 1)) / (xTickCount - 1));
          return { label: data[idx]?.date.slice(5) ?? "" };
        });

  return (
    <div>
      <div className="chart-box" ref={ref}>
        {width > 0 && height > 0 && (
          <svg width={width} height={height}>
            <defs>
              <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={color} stopOpacity={0.22} />
                <stop offset="100%" stopColor={color} stopOpacity={0} />
              </linearGradient>
            </defs>
            {yTicks.map((t, i) => (
              <line key={i} x1={PAD_LEFT} x2={width - PAD_RIGHT} y1={t.y} y2={t.y} stroke="#252b38" />
            ))}
            {yTicks.map((t, i) => (
              <text key={i} x={PAD_LEFT - 8} y={t.y} textAnchor="end" dominantBaseline="middle" fontSize={10} fill="#565e70">
                {Math.round(t.value).toLocaleString()}
              </text>
            ))}
            <path d={areaPath} fill="url(#priceFill)" stroke="none" />
            <path d={linePath} fill="none" stroke={color} strokeWidth={2} />
          </svg>
        )}
      </div>
      <div className="chart-x-labels">
        {xTicks.map((t, i) => (
          <span key={i}>{t.label}</span>
        ))}
      </div>
    </div>
  );
}
