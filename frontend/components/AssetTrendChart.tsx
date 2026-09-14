"use client";

import { useElementSize } from "@/lib/useElementSize";
import type { TrendPoint } from "@/lib/types";

const PAD_LEFT = 56;
const PAD_RIGHT = 8;
const PAD_TOP = 8;
const PAD_BOTTOM = 20;
const Y_TICKS = 4;
const COLOR = "#ffb74a";

export default function AssetTrendChart({ data }: { data: TrendPoint[] }) {
  const { ref, width, height } = useElementSize<HTMLDivElement>();

  if (data.length === 0) {
    return <div className="empty-state">표시할 자산 추이 데이터가 없습니다</div>;
  }

  const values = data.map((d) => d.value);
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const range = maxValue - minValue;

  const plotWidth = Math.max(0, width - PAD_LEFT - PAD_RIGHT);
  const plotHeight = Math.max(0, height - PAD_TOP - PAD_BOTTOM);

  // PriceChart와 동일한 이유(recharts AreaChart/YAxis 스케일 계산이 이 환경에서 깨짐)로 직접 계산하는 SVG로 대체.
  // range가 0(데이터가 1개뿐이거나 값이 전부 동일)이면 바닥에 붙지 않도록 수직 중앙에 그린다.
  const points = data.map((d, i) => ({
    x: PAD_LEFT + (data.length === 1 ? plotWidth / 2 : (i / (data.length - 1)) * plotWidth),
    y: range === 0 ? PAD_TOP + plotHeight / 2 : PAD_TOP + (1 - (d.value - minValue) / range) * plotHeight,
  }));

  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
  const floorY = PAD_TOP + plotHeight;
  const areaPath = `${linePath} L${points[points.length - 1].x.toFixed(1)},${floorY} L${points[0].x.toFixed(1)},${floorY} Z`;

  const yTicks = Array.from({ length: Y_TICKS + 1 }, (_, i) => ({
    value: maxValue - (i * range) / Y_TICKS,
    y: PAD_TOP + (i / Y_TICKS) * plotHeight,
  }));

  const xTickCount = Math.min(6, data.length);
  const xTicks =
    xTickCount <= 1
      ? [{ label: data[0]?.date.slice(5) ?? "", x: points[0]?.x ?? 0 }]
      : Array.from({ length: xTickCount }, (_, i) => {
          const idx = Math.round((i * (data.length - 1)) / (xTickCount - 1));
          return { label: data[idx]?.date.slice(5) ?? "", x: points[idx]?.x ?? 0 };
        });

  return (
    <div className="chart-box" ref={ref}>
      {width > 0 && height > 0 && (
        <svg width={width} height={height}>
          <defs>
            <linearGradient id="assetTrendFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={COLOR} stopOpacity={0.28} />
              <stop offset="100%" stopColor={COLOR} stopOpacity={0} />
            </linearGradient>
          </defs>
          {yTicks.map((t, i) => (
            <line key={i} x1={PAD_LEFT} x2={width - PAD_RIGHT} y1={t.y} y2={t.y} stroke="#252b38" />
          ))}
          {yTicks.map((t, i) => (
            <text key={i} x={PAD_LEFT - 8} y={t.y} textAnchor="end" dominantBaseline="middle" fontSize={10} fill="#565e70">
              {(t.value / 10000).toLocaleString()}만
            </text>
          ))}
          {xTicks.map((t, i) => (
            <text
              key={i}
              x={t.x}
              y={height - 4}
              textAnchor={i === 0 ? "start" : i === xTicks.length - 1 ? "end" : "middle"}
              fontSize={10}
              fill="#565e70"
            >
              {t.label}
            </text>
          ))}
          <path d={areaPath} fill="url(#assetTrendFill)" stroke="none" />
          <path d={linePath} fill="none" stroke={COLOR} strokeWidth={2} />
          {points.length === 1 && <circle cx={points[0].x} cy={points[0].y} r={3} fill={COLOR} />}
        </svg>
      )}
    </div>
  );
}
