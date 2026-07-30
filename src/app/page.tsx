export default function Home() {
  return (
    <main className="shell">
      <h1>FamilyShield</h1>
      <p className="muted">
        FamilyShield&apos;s Android clients connect to this API service.
      </p>
      <div className="landing-cards">
        <a className="card" href="/api/docs">
          <h2>API documentation</h2>
          <p className="muted">Open the interactive Swagger documentation.</p>
        </a>
        <a className="card" href="/api/health">
          <h2>Service health</h2>
          <p className="muted">Check API and database availability.</p>
        </a>
      </div>
    </main>
  );
}
