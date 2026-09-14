export default function TradeLoading() {
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>매수 / 매도</h1>
        </div>
      </div>
      <div className="grid grid-2">
        <div className="card">
          <div className="skeleton" style={{ height: 320 }} />
        </div>
        <div className="card">
          <div className="skeleton" style={{ height: 320 }} />
        </div>
      </div>
    </section>
  );
}
