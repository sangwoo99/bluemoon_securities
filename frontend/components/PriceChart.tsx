"use client";

import { useState } from "react";
import { useElementSize } from "@/lib/useElementSize";
import type { PricePoint } from "@/lib/types";

const PAD_LEFT = 64;
const PAD_RIGHT = 8;
const PAD_TOP = 8;
const PAD_BOTTOM = 4;
const Y_TICKS = 4;

/** 데이터 개수에 비례해 이동평균/볼린저 밴드 기간을 정한다 — 표준(20)을 쓰면 "1개월"처럼 짧은
 * 기간은 실제 영업일 수(~24일)에서 20일치를 빼고 나면 앞부분 대부분이 안 그려진다. 데이터 길이의
 * 1/3(최소 5, 최대 20)로 잡아 어느 기간이든 절반 이상은 선이 보이게 한다. */
function movingAverageWindow(length: number): number {
  return Math.max(5, Math.min(20, Math.floor(length / 3)));
}

function computeIndicators(prices: number[], window: number) {
  const out: { index: number; sma: number; upper: number; lower: number }[] = [];
  for (let i = window - 1; i < prices.length; i++) {
    const slice = prices.slice(i - window + 1, i + 1);
    const mean = slice.reduce((a, b) => a + b, 0) / window;
    const variance = slice.reduce((a, b) => a + (b - mean) ** 2, 0) / window;
    const stddev = Math.sqrt(variance);
    out.push({ index: i, sma: mean, upper: mean + 2 * stddev, lower: mean - 2 * stddev });
  }
  return out;
}

export default function PriceChart({
  data,
  up,
  referencePrice,
  referenceLabel,
  showIndicators = false,
}: {
  data: PricePoint[];
  up: boolean;
  /** 전일종가(당일 차트) 또는 내 평단가(기간 차트)처럼, 가로 점선으로 표시할 기준가. */
  referencePrice?: number;
  referenceLabel?: string;
  /** 이동평균선/볼린저 밴드 표시 여부 (기간이 너무 짧으면(당일/1주) 의미가 없어 호출하는 쪽에서 끈다). */
  showIndicators?: boolean;
}) {
  const { ref, width, height } = useElementSize<HTMLDivElement>();
  const [showCurrent, setShowCurrent] = useState(false);

  if (data.length === 0) {
    return <div className="empty-state">표시할 시세 데이터가 없습니다</div>;
  }

  const color = up ? "#FF5C5C" : "#4C8DFF";
  const prices = data.map((d) => d.price);
  const maWindow = movingAverageWindow(data.length);
  const indicators = showIndicators && data.length >= maWindow ? computeIndicators(prices, maWindow) : [];

  const hasReference = typeof referencePrice === "number";
  const allValues = [
    ...prices,
    ...(hasReference ? [referencePrice] : []),
    ...indicators.flatMap((d) => [d.upper, d.lower]),
  ];
  const minPrice = Math.min(...allValues);
  const maxPrice = Math.max(...allValues);
  const range = maxPrice - minPrice;

  const plotWidth = Math.max(0, width - PAD_LEFT - PAD_RIGHT);
  const plotHeight = Math.max(0, height - PAD_TOP - PAD_BOTTOM);

  const scaleX = (i: number) => PAD_LEFT + (data.length === 1 ? plotWidth / 2 : (i / (data.length - 1)) * plotWidth);
  const scaleY = (price: number) => (range === 0 ? PAD_TOP + plotHeight / 2 : PAD_TOP + (1 - (price - minPrice) / range) * plotHeight);

  // recharts의 AreaChart/YAxis가 이 환경에서 스케일 계산이 깨져(값이 낮을수록 위로, 눈금도 한 줄에 겹쳐 그려짐)
  // 그래프 방향이 실제와 반대로 보이는 문제가 있어, 좌표를 직접 계산하는 순수 SVG로 대체한다.
  // range가 0(가격이 하루뿐이거나 전부 동일)이면 바닥에 붙지 않도록 수직 중앙에 그린다.
  const points = data.map((d, i) => ({
    x: scaleX(i),
    y: scaleY(d.price),
    price: d.price,
  }));

  const indicatorPoints = indicators.map((d) => ({
    x: scaleX(d.index),
    sma: scaleY(d.sma),
    upper: scaleY(d.upper),
    lower: scaleY(d.lower),
  }));
  const smaPath = indicatorPoints.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.sma.toFixed(1)}`).join(" ");
  const upperPath = indicatorPoints.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.upper.toFixed(1)}`).join(" ");
  const lowerPath = indicatorPoints.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.lower.toFixed(1)}`).join(" ");
  const bandFillPath =
    indicatorPoints.length > 0
      ? `${upperPath} L${[...indicatorPoints].reverse().map((p) => `${p.x.toFixed(1)},${p.lower.toFixed(1)}`).join(" L")} Z`
      : "";

  const linePath = points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(" ");
  const floorY = PAD_TOP + plotHeight;
  const areaPath = `${linePath} L${points[points.length - 1].x.toFixed(1)},${floorY} L${points[0].x.toFixed(1)},${floorY} Z`;

  const yTicks = Array.from({ length: Y_TICKS + 1 }, (_, i) => ({
    value: maxPrice - (i * range) / Y_TICKS,
    y: PAD_TOP + (i / Y_TICKS) * plotHeight,
  }));

  const referenceY = hasReference
    ? range === 0
      ? PAD_TOP + plotHeight / 2
      : PAD_TOP + (1 - (referencePrice! - minPrice) / range) * plotHeight
    : null;

  // date 필드는 호출하는 쪽에서 이미 화면에 보여줄 형태(예: "09-01", "09:30")로 가공해서 넘겨준다.
  const xTickCount = Math.min(6, data.length);
  const xTicks =
    xTickCount <= 1
      ? [{ label: data[0]?.date ?? "" }]
      : Array.from({ length: xTickCount }, (_, i) => {
          const idx = Math.round((i * (data.length - 1)) / (xTickCount - 1));
          return { label: data[idx]?.date ?? "" };
        });

  return (
    <div>
      <div className="chart-box" ref={ref}>
        {width > 0 && height > 0 && (
          <svg width={width} height={height} onMouseEnter={() => setShowCurrent(true)} onMouseLeave={() => setShowCurrent(false)}>
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
            {bandFillPath && <path d={bandFillPath} fill="#8b93a7" fillOpacity={0.06} stroke="none" />}
            <path d={areaPath} fill="url(#priceFill)" stroke="none" />
            <path d={linePath} fill="none" stroke={color} strokeWidth={2} />
            {indicatorPoints.length > 0 && (
              <>
                <path d={upperPath} fill="none" stroke="#8b93a7" strokeWidth={1} strokeDasharray="3 3" />
                <path d={lowerPath} fill="none" stroke="#8b93a7" strokeWidth={1} strokeDasharray="3 3" />
                <path d={smaPath} fill="none" stroke="#ffb74a" strokeWidth={1.5} />
                <text x={PAD_LEFT} y={PAD_TOP + 10} fontSize={10} fill="#ffb74a">
                  MA{maWindow}
                </text>
                <text x={PAD_LEFT + 48} y={PAD_TOP + 10} fontSize={10} fill="#8b93a7">
                  볼린저밴드
                </text>
              </>
            )}
            {referenceY !== null && (
              <>
                <line
                  x1={PAD_LEFT}
                  x2={width - PAD_RIGHT}
                  y1={referenceY}
                  y2={referenceY}
                  stroke="#8b93a7"
                  strokeWidth={1}
                  strokeDasharray="4 3"
                />
                {(() => {
                  const label = `${referenceLabel ?? "기준가"} ${Math.round(referencePrice!).toLocaleString()}`;
                  const labelWidth = label.length * 6 + 8;
                  const labelX = PAD_LEFT + (width - PAD_LEFT - PAD_RIGHT) / 2 - labelWidth / 2;
                  const labelCenterY = Math.max(PAD_TOP + 8, referenceY - 14);
                  return (
                    <g>
                      <rect x={labelX} y={labelCenterY - 8} width={labelWidth} height={16} rx={3} fill="#12161f" fillOpacity={0.9} />
                      <text x={labelX + labelWidth / 2} y={labelCenterY} textAnchor="middle" dominantBaseline="middle" fontSize={10} fill="#8b93a7">
                        {label}
                      </text>
                    </g>
                  );
                })()}
              </>
            )}
            {(() => {
              const last = points[points.length - 1];
              return (
                <>
                  <circle cx={last.x} cy={last.y} r={3} fill={color} />
                  {showCurrent &&
                    (() => {
                      const label = Math.round(last.price).toLocaleString();
                      const labelWidth = label.length * 6 + 8;
                      const labelX = Math.max(PAD_LEFT, width - PAD_RIGHT - labelWidth);
                      const labelCenterY = last.y - 14 >= PAD_TOP + 8 ? last.y - 14 : last.y + 14;
                      return (
                        <g>
                          <rect x={labelX} y={labelCenterY - 8} width={labelWidth} height={16} rx={3} fill="#12161f" fillOpacity={0.9} />
                          <text x={labelX + labelWidth / 2} y={labelCenterY} textAnchor="middle" dominantBaseline="middle" fontSize={10} fill={color}>
                            {label}
                          </text>
                        </g>
                      );
                    })()}
                </>
              );
            })()}
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
