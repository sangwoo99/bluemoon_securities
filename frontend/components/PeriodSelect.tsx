"use client";

import { useRouter, useSearchParams } from "next/navigation";
import type { TrendPeriod } from "@/lib/types";

const PERIODS: { value: TrendPeriod; label: string }[] = [
  { value: "1W", label: "1주" },
  { value: "1M", label: "1개월" },
  { value: "3M", label: "3개월" },
  { value: "1Y", label: "1년" },
];

export default function PeriodSelect({ current }: { current: TrendPeriod }) {
  const router = useRouter();
  const searchParams = useSearchParams();

  function onChange(period: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("period", period);
    router.push(`/dashboard?${params.toString()}`);
  }

  return (
    <select
      className="chip"
      style={{ background: "var(--surface)", fontFamily: "var(--font-inter)", border: "1px solid var(--border)" }}
      value={current}
      onChange={(e) => onChange(e.target.value)}
    >
      {PERIODS.map((p) => (
        <option key={p.value} value={p.value}>
          {p.label}
        </option>
      ))}
    </select>
  );
}
