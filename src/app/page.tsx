export default function Home() {
  return (
    <main className="shell">
      <h1>FamilyShield</h1>
      <p className="muted">
        Keep track of your family&apos;s devices — see where they are, their battery,
        and get alerts. Choose where to go:
      </p>
      <div className="landing-cards">
        <a className="card" href="/parent">
          <h2>👨‍👩‍👧 Parent dashboard</h2>
          <p className="muted">
            Sign in, add a child, generate a pairing code, and watch their live
            location, status, and alerts on the map.
          </p>
        </a>
        <a className="card" href="/kid">
          <h2>📱 Kid device simulator</h2>
          <p className="muted">
            Pair this &quot;device&quot; with a code from the parent, then send
            location and battery updates.
          </p>
        </a>
      </div>
    </main>
  );
}
