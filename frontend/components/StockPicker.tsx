import Link from "next/link";

export interface StockPickerItem {
  stockCode: string;
  stockName: string;
}

export default function StockPicker({ items, activeCode }: { items: StockPickerItem[]; activeCode: string }) {
  if (items.length === 0) return null;

  return (
    <div className="stock-picker">
      {items.map((item) => (
        <Link
          key={item.stockCode}
          href={`/stocks/${item.stockCode}`}
          className={`chip${item.stockCode === activeCode ? " active" : ""}`}
        >
          {item.stockName}
        </Link>
      ))}
    </div>
  );
}
