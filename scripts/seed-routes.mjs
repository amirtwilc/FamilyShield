// Seeds a recurring-route history (home → school → home over two days) for the
// demo family's first child, so the parent app's "Frequent routes" populates.
//   node scripts/seed-routes.mjs
const BASE = process.env.SEED_BASE_URL || 'http://localhost:3000';
const EMAIL = process.env.SEED_EMAIL || 'demo@familyshield.app';
const PASSWORD = process.env.SEED_PASSWORD || 'Demo1234';
const HOME = { lat: 32.0853, lng: 34.7818 };
const SCHOOL = { lat: 32.1020, lng: 34.8000 };

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const t = await res.text();
  return { status: res.status, data: t ? JSON.parse(t) : null };
}

function build() {
  const pts = [];
  const now = Date.now();
  // Two days; each day: home (stay) -> school (move) -> stay -> home (move).
  for (let day = 2; day >= 1; day--) {
    let t = now - day * 24 * 3600 * 1000 + 6 * 3600 * 1000; // ~06:00 that day
    const push = (p) => pts.push({ lat: p.lat, lng: p.lng, recorded_at: new Date(t).toISOString() });
    const stay = (p, mins) => { for (let m = 0; m < mins; m += 3) { push(p); t += 3 * 60000; } };
    const move = (a, b, mins) => { for (let i = 1; i <= 5; i++) { const f = i / 5; push({ lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f }); t += (mins / 5) * 60000; } };
    stay(HOME, 15); move(HOME, SCHOOL, 20); stay(SCHOOL, 15); move(SCHOOL, HOME, 20); stay(HOME, 15);
  }
  return pts;
}

async function main() {
  let auth = await api('/api/auth/login', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  if (auth.status !== 200) auth = await api('/api/auth/register', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  const token = auth.data.accessToken;
  const child = (await api('/api/children', { token })).data.children[0];
  if (!child) throw new Error('no children — run seed-example-data.mjs first');

  const code = (await api(`/api/children/${child.id}/pairing-code`, { method: 'POST', token })).data.code;
  const deviceToken = (await api('/api/pair', { method: 'POST', body: { code, platform: 'android', model: 'RouteSeed' } })).data.deviceToken;

  const points = build();
  for (let i = 0; i < points.length; i += 180) {
    await api('/api/locations', { method: 'POST', token: deviceToken, body: { points: points.slice(i, i + 180) } });
  }

  const routes = await api(`/api/children/${child.id}/routes`, { token });
  console.log(`• sent ${points.length} points for ${child.displayName}`);
  console.log(`• detected ${routes.data.frequent.length} frequent route(s), ${routes.data.trips.length} trip(s)`);
  routes.data.frequent.forEach((r) => console.log(`   ${r.from.lat.toFixed(4)},${r.from.lng.toFixed(4)} -> ${r.to.lat.toFixed(4)},${r.to.lng.toFixed(4)}  ×${r.count}`));
}

main().catch((e) => { console.error('seed-routes failed:', e.message); process.exit(1); });
