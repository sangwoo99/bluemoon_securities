import HoldingsTable from "@/components/HoldingsTable";
import { apiGet } from "@/lib/api";
import type { Holding } from "@/lib/types";

export const dynamic = "force-dynamic";

export default async function HoldingsPage() {
  let holdings: Holding[] = [];
  try {
    holdings = (await apiGet<Holding[]>("/api/holdings")) ?? [];
  } catch {
    holdings = [];
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>보유 종목</h1>
          <p>평단가와 현재가를 비교해 손익을 확인하세요</p>
        </div>
      </div>
      <div className="card">
        <HoldingsTable holdings={holdings} variant="full" />
      </div>
    </section>
  );
}
