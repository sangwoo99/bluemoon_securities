export default function StockDetailLoading() {
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>종목 상세</h1>
        </div>
      </div>
      <div className="skeleton" style={{ height: 32, width: 200, marginBottom: 18 }} />
      <div className="grid grid-2">
        <div className="card">
          <div className="skeleton" style={{ height: 230 }} />
        </div>
        <div className="card">
          <div className="skeleton" style={{ height: 230 }} />
        </div>
      </div>
    </section>
  );
}
