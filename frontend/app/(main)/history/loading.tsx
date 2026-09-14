export default function HistoryLoading() {
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>거래 내역</h1>
        </div>
      </div>
      <div className="card">
        <div className="skeleton" style={{ height: 240 }} />
      </div>
    </section>
  );
}
