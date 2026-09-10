import Link from "next/link";
import type { Holding } from "@/lib/types";

export default function StockPicker({ holdings, activeCode }: { holdings: Holding[]; activeCode: string }) {
  if (holdings.length === 0) return null;

  return (
    <div className="stock-picker">
      {holdings.map((h) => (
        <Link key={h.stockCode} href={`/stocks/${h.stockCode}`} className={`chip${h.stockCode === activeCode ? " active" : ""}`}>
          {h.stockName}
        </Link>
      ))}
    </div>
  );
}
