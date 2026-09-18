"use client";

import { useState, useTransition } from "react";
import { useRouter, usePathname, useSearchParams } from "next/navigation";
import PriceChart from "@/components/PriceChart";
import type { PricePoint } from "@/lib/types";

const PERIODS: { value: string; label: string }[] = [
  { value: "1d", label: "당일" },
  { value: "1w", label: "1주" },
  { value: "1m", label: "1개월" },
  { value: "3m", label: "3개월" },
  { value: "1y", label: "1년" },
];

/**
 * "시세 추이" 카드 전체(제목 + 지표 체크박스 + 기간 칩 + 그래프)를 관리한다. "당일"은 캐시 없이
 * KIS를 직접 불러 응답이 느릴 수 있어(StockService 참고), useTransition의 isPending으로 전환 중임을
 * 바로 보여준다. searchParams만 바뀌는 네비게이션은 이 라우트 세그먼트의 loading.tsx가 뜨지 않아
 * 별도 처리가 필요하다. 이동평균선/볼린저 밴드는 체크했을 때만 보이는 순수 클라이언트 상태라 여기서
 * 갖고 있다가 PriceChart에 넘긴다.
 */
export default function StockPriceCard({
  current,
  data,
  up,
  referencePrice,
  referenceLabel,
  extendedPrices,
  canShowIndicators,
}: {
  current: string;
  data: PricePoint[];
  up: boolean;
  referencePrice?: number;
  referenceLabel?: string;
  extendedPrices?: number[];
  canShowIndicators: boolean;
}) {
  const [isPending, startTransition] = useTransition();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [showMA, setShowMA] = useState(false);
  const [showBollinger, setShowBollinger] = useState(false);

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
      <div className="card-head">
        <h3>시세 추이</h3>
        {canShowIndicators && (
          <div className="indicator-toggles">
            <label className="indicator-toggle">
              <input type="checkbox" checked={showMA} onChange={(e) => setShowMA(e.target.checked)} />
              이동평균선
            </label>
            <label className="indicator-toggle">
              <input type="checkbox" checked={showBollinger} onChange={(e) => setShowBollinger(e.target.checked)} />
              볼린저밴드
            </label>
          </div>
        )}
      </div>
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
        <div className={isPending ? "chart-loading-content" : undefined}>
          <PriceChart
            data={data}
            up={up}
            referencePrice={referencePrice}
            referenceLabel={referenceLabel}
            extendedPrices={canShowIndicators ? extendedPrices : undefined}
            showMA={canShowIndicators && showMA}
            showBollinger={canShowIndicators && showBollinger}
          />
        </div>
        {isPending && (
          <div className="chart-loading-overlay">
            <span className="spinner" />
          </div>
        )}
      </div>
    </div>
  );
}
