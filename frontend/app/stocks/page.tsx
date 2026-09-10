import { redirect } from "next/navigation";
import { apiGet } from "@/lib/api";
import type { Holding } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function StocksIndexPage() {
  let holdings: Holding[] = [];
  try {
    holdings = (await apiGet<Holding[]>("/api/holdings")) ?? [];
  } catch {
    holdings = [];
  }

  if (holdings.length > 0) {
    redirect(`/stocks/${holdings[0].stockCode}`);
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>종목 상세</h1>
        </div>
      </div>
      <div className="empty-state">
        <div className="icon">📭</div>
        아직 보유한 종목이 없습니다 — <a href="/trade">매수하러 가기</a>
      </div>
    </section>
  );
}
