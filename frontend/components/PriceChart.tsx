"use client";

import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";
import { useElementSize } from "@/lib/useElementSize";
import type { PricePoint } from "@/lib/types";

export default function PriceChart({ data, up }: { data: PricePoint[]; up: boolean }) {
  const { ref, width, height } = useElementSize<HTMLDivElement>();

  if (data.length === 0) {
    return <div className="empty-state">표시할 시세 데이터가 없습니다</div>;
  }

  const color = up ? "#FF5C5C" : "#4C8DFF";

  return (
    <div className="chart-box" ref={ref}>
      {width > 0 && height > 0 && (
        <AreaChart width={width} height={height} data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="priceFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity={0.22} />
              <stop offset="100%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="#252b38" vertical={false} />
          <XAxis dataKey="date" tick={false} axisLine={false} tickLine={false} />
          <YAxis tick={{ fill: "#565e70", fontSize: 10 }} axisLine={false} tickLine={false} domain={["auto", "auto"]} width={56} />
          <Tooltip
            contentStyle={{ background: "#1a2029", border: "1px solid #252b38", borderRadius: 8, fontSize: 12 }}
            labelStyle={{ color: "#8a92a6" }}
            formatter={(value: number) => [`${value.toLocaleString()}원`, "종가"]}
          />
          <Area type="monotone" dataKey="price" stroke={color} strokeWidth={2} fill="url(#priceFill)" />
        </AreaChart>
      )}
    </div>
  );
}
