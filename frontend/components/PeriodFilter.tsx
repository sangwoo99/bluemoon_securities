"use client";

import { useRouter, usePathname, useSearchParams } from "next/navigation";

const PERIODS: { value: string; label: string }[] = [
  { value: "1d", label: "당일" },
  { value: "1w", label: "1주" },
  { value: "1m", label: "1개월" },
  { value: "3m", label: "3개월" },
  { value: "1y", label: "1년" },
];

export default function PeriodFilter({ current }: { current: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function onChange(period: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("period", period);
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <div className="stock-picker">
      {PERIODS.map((p) => (
        <button
          key={p.value}
          type="button"
          className={`chip${current === p.value ? " active" : ""}`}
          onClick={() => onChange(p.value)}
        >
          {p.label}
        </button>
      ))}
    </div>
  );
}
