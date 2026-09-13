"use client";

import { useRouter, useSearchParams } from "next/navigation";

const RANK_TYPES: { value: string; label: string }[] = [
  { value: "all", label: "전체 종목" },
  { value: "fluctuation", label: "상승률 TOP10" },
  { value: "volume", label: "거래량 TOP10" },
];

export default function RankFilter({ current }: { current: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();

  function onChange(rank: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("rank", rank);
    router.push(`/stocks?${params.toString()}`);
  }

  return (
    <div className="stock-picker">
      {RANK_TYPES.map((r) => (
        <button
          key={r.value}
          type="button"
          className={`chip${current === r.value ? " active" : ""}`}
          onClick={() => onChange(r.value)}
        >
          {r.label}
        </button>
      ))}
    </div>
  );
}
