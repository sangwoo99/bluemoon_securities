"use client";

import { ResponsiveContainer, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";
import type { TrendPoint } from "@/lib/types";

export default function AssetTrendChart({ data }: { data: TrendPoint[] }) {
  if (data.length === 0) {
    return <div className="empty-state">표시할 자산 추이 데이터가 없습니다</div>;
  }

  return (
    <div className="chart-box">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="assetTrendFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#ffb74a" stopOpacity={0.28} />
              <stop offset="100%" stopColor="#ffb74a" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke="#252b38" vertical={false} />
          <XAxis dataKey="date" tick={{ fill: "#565e70", fontSize: 10 }} axisLine={false} tickLine={false} />
          <YAxis
            tick={{ fill: "#565e70", fontSize: 10 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v: number) => `${(v / 10000).toLocaleString()}만`}
            width={48}
          />
          <Tooltip
            contentStyle={{ background: "#1a2029", border: "1px solid #252b38", borderRadius: 8, fontSize: 12 }}
            labelStyle={{ color: "#8a92a6" }}
            formatter={(value: number) => [`${value.toLocaleString()}원`, "평가금액"]}
          />
          <Area type="monotone" dataKey="value" stroke="#ffb74a" strokeWidth={2} fill="url(#assetTrendFill)" />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
