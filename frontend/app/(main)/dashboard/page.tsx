import Link from "next/link";
import SummaryCards from "@/components/SummaryCards";
import AssetTrendChart from "@/components/AssetTrendChart";
import AiInsightCarousel from "@/components/AiInsightCarousel";
import HoldingsTable from "@/components/HoldingsTable";
import AllocationChart from "@/components/AllocationChart";
import PeriodSelect from "@/components/PeriodSelect";
import { apiGet } from "@/lib/api";
import type { Holding, Insight, PortfolioSummary, TrendPeriod, TrendPoint } from "@/lib/types";

export const dynamic = "force-dynamic";

const HOLDINGS_PREVIEW_COUNT = 3;

async function safeGet<T>(path: string): Promise<T | null> {
  try {
    return await apiGet<T>(path);
  } catch {
    return null;
  }
}

export default async function DashboardPage({ searchParams }: { searchParams: Promise<{ period?: string }> }) {
  const resolvedSearchParams = await searchParams;
  const period = (resolvedSearchParams.period ?? "1M") as TrendPeriod;

  const [summary, trend, holdings, insights] = await Promise.all([
    safeGet<PortfolioSummary>("/api/portfolio/summary"),
    safeGet<TrendPoint[]>(`/api/portfolio/trend?period=${period}`),
    safeGet<Holding[]>("/api/holdings"),
    safeGet<Insight[]>("/api/insights/today"),
  ]);

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>대시보드</h1>
          <p>보유 자산 현황과 오늘의 AI 인사이트를 확인하세요</p>
        </div>
      </div>

      {summary ? (
        <SummaryCards summary={summary} />
      ) : (
        <div className="empty-state">포트폴리오 요약을 불러올 수 없습니다.</div>
      )}

      <div className="grid grid-2" style={{ marginBottom: 16 }}>
        <div className="card chart-fill">
          <div className="card-head">
            <h3>자산 변화 추이</h3>
            <PeriodSelect current={period} />
          </div>
          <AssetTrendChart data={trend ?? []} />
        </div>

        <AiInsightCarousel insights={insights ?? []} />
      </div>

      <div className="grid grid-2">
        <div className="card">
          <div className="card-head">
            <h3>보유 종목</h3>
            <Link href="/holdings" className="card-link">
              상세보기 →
            </Link>
          </div>
          <HoldingsTable holdings={(holdings ?? []).slice(0, HOLDINGS_PREVIEW_COUNT)} variant="compact" />
        </div>
        <div className="card chart-fill">
          <div className="card-head">
            <h3>자산 배분</h3>
          </div>
          <AllocationChart holdings={holdings ?? []} />
        </div>
      </div>
    </section>
  );
}
