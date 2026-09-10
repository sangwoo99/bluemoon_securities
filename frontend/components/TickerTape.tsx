import type { Holding } from "@/lib/types";

export default function TickerTape({ holdings }: { holdings: Holding[] }) {
  if (holdings.length === 0) {
    return <div className="ticker-wrap" />;
  }

  const items = holdings.map((h) => {
    const up = h.evalGainRate >= 0;
    const cls = up ? "tup" : "tdown";
    const arrow = up ? "▲" : "▼";
    return (
      <span className="ticker-item" key={h.stockCode}>
        <span className="tname">{h.stockName}</span>
        <span className={`${cls} mono`}>{h.currentPrice.toLocaleString()}</span>
        <span className={`${cls} tarrow mono`}>
          {arrow} {Math.abs(h.evalGainRate).toFixed(2)}%
        </span>
      </span>
    );
  });

  return (
    <div className="ticker-wrap">
      <div className="ticker-track">
        {items}
        {items}
      </div>
    </div>
  );
}
