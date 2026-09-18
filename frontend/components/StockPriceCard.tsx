"use client";

import { useTransition } from "react";
import type { ReactNode } from "react";
import { useRouter, usePathname, useSearchParams } from "next/navigation";

const PERIODS: { value: string; label: string }[] = [
  { value: "1d", label: "당일" },
  { value: "1w", label: "1주" },
  { value: "1m", label: "1개월" },
  { value: "3m", label: "3개월" },
  { value: "1y", label: "1년" },
];

/**
 * 기간 칩 + 시세 그래프를 함께 관리한다. "당일"은 캐시 없이 KIS를 직접 불러 응답이 느릴 수 있어
 * (StockService 참고), useTransition의 isPending으로 전환 중임을 바로 보여준다. searchParams만
 * 바뀌는 네비게이션은 이 라우트 세그먼트의 loading.tsx가 뜨지 않아 별도 처리가 필요하다.
 */
export default function StockPriceCard({ current, children }: { current: string; children: ReactNode }) {
  const [isPending, startTransition] = useTransition();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function onChange(period: string) {
    if (period === current) return;
    const params = new URLSearchParams(searchParams.toString());
    params.set("period", period);
    startTransition(() => {
      router.push(`${pathname}?${params.toString()}`);
    });
  }

  return (
    <div>
      <div className="stock-picker">
        {PERIODS.map((p) => (
          <button
            key={p.value}
            type="button"
            className={`chip${current === p.value ? " active" : ""}${isPending ? " disabled" : ""}`}
            onClick={() => onChange(p.value)}
          >
            {p.label}
          </button>
        ))}
      </div>
      <div className="chart-loading-wrap">
        <div className={isPending ? "chart-loading-content" : undefined}>{children}</div>
        {isPending && (
          <div className="chart-loading-overlay">
            <span className="spinner" />
          </div>
        )}
      </div>
    </div>
  );
}
