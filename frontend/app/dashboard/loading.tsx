export default function DashboardLoading() {
  return (
    <section>
      <div className="page-head">
        <div>
          <h1>대시보드</h1>
        </div>
      </div>
      <div className="summary-row">
        {[0, 1, 2].map((i) => (
          <div className="card summary-card" key={i}>
            <div className="skeleton" style={{ height: 12, width: "60%", marginBottom: 12 }} />
            <div className="skeleton" style={{ height: 26, width: "80%" }} />
          </div>
        ))}
      </div>
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
