export default function HoldingsLoading() {
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>보유 종목</h1>
        </div>
      </div>
      <div className="card">
        <div className="skeleton" style={{ height: 240 }} />
      </div>
    </section>
  );
}
