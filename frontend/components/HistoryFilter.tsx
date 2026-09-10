"use client";

import { useRouter } from "next/navigation";
import type { Holding } from "@/lib/types";

export default function HistoryFilter({ holdings, current }: { holdings: Holding[]; current: string }) {
  const router = useRouter();

  return (
    <div className="filter-row">
      <select
        className="chip"
        style={{ background: "var(--surface)", fontFamily: "var(--font-inter)", border: "1px solid var(--border)" }}
        value={current}
        onChange={(e) => {
          const code = e.target.value;
          router.push(code === "all" ? "/history" : `/history?code=${code}`);
        }}
      >
        <option value="all">전체 종목</option>
        {holdings.map((h) => (
          <option key={h.stockCode} value={h.stockCode}>
            {h.stockName}
          </option>
        ))}
      </select>
    </div>
  );
}
