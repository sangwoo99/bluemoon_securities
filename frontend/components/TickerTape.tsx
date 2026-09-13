export interface TickerItem {
  code: string;
  name: string;
  price: number;
  rate: number;
}

export default function TickerTape({ items }: { items: TickerItem[] }) {
  if (items.length === 0) {
    return <div className="ticker-wrap" />;
  }

  const rendered = items.map((it) => {
    const up = it.rate >= 0;
    const cls = up ? "tup" : "tdown";
    const arrow = up ? "▲" : "▼";
    return (
      <span className="ticker-item" key={it.code}>
        <span className="tname">{it.name}</span>
        <span className={`${cls} mono`}>{it.price.toLocaleString()}</span>
        <span className={`${cls} tarrow mono`}>
          {arrow} {Math.abs(it.rate).toFixed(2)}%
        </span>
      </span>
    );
  });

  return (
    <div className="ticker-wrap">
      <div className="ticker-track">
        {rendered}
        {rendered}
      </div>
    </div>
  );
}
