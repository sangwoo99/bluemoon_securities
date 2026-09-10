"use client";

import { ResponsiveContainer, PieChart, Pie, Cell, Legend, Tooltip } from "recharts";
import type { Holding } from "@/lib/types";

const COLORS = ["#FFB74A", "#FF5C5C", "#4C8DFF", "#8A92A6", "#3DDC97", "#B98CFF"];

export default function AllocationChart({ holdings }: { holdings: Holding[] }) {
  if (holdings.length === 0) {
    return <div className="empty-state">표시할 보유 종목이 없습니다</div>;
  }

  const data = holdings.map((h) => ({ name: h.stockName, value: h.evalValue }));

  return (
    <div className="chart-box small">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="name" innerRadius="62%" outerRadius="90%" paddingAngle={2} stroke="#12161f" strokeWidth={3}>
            {data.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{ background: "#1a2029", border: "1px solid #252b38", borderRadius: 8, fontSize: 12 }}
            formatter={(value: number) => `${value.toLocaleString()}원`}
          />
          <Legend layout="vertical" align="right" verticalAlign="middle" iconSize={10} formatter={(v) => <span style={{ color: "#8a92a6", fontSize: 11 }}>{v}</span>} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
