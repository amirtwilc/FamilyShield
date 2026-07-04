// Seeds a richer demo dataset: one parent with several children, each paired to a
// device reporting a location + battery (one low enough to trigger a low-battery
// alert). Idempotent-ish: reuses the parent and skips children that already exist.
//
//   node scripts/seed-example-data.mjs
//
// Backend must be running (npm run dev, or the docker `backend` service).

const BASE = process.env.SEED_BASE_URL || 'http://localhost:3000';
const EMAIL = process.env.SEED_EMAIL || 'demo@familyshield.app';
const PASSWORD = process.env.SEED_PASSWORD || 'Demo1234';

// name, [lat, lng], battery%, charging
const KIDS = [
  ['Mia',   [32.0853, 34.7818], 82, false],
  ['Noah',  [32.0719, 34.7915], 12, false], // low battery -> alert
  ['Lily',  [32.1093, 34.8555], 64, true],
  ['Ethan', [32.0500, 34.7600], 38, false],
];

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}

async function main() {
  let auth = await api('/api/auth/register', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  if (auth.status === 409) auth = await api('/api/auth/login', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  const token = auth.data?.accessToken;
  if (!token) throw new Error('no token: ' + JSON.stringify(auth.data));

  const existing = (await api('/api/children', { token })).data?.children ?? [];
  const have = new Set(existing.map((c) => c.displayName));

  for (const [name, [lat, lng], battery, charging] of KIDS) {
    if (have.has(name)) { console.log(`• ${name} already exists — skipping`); continue; }
    const child = (await api('/api/children', { method: 'POST', body: { displayName: name }, token })).data;
    const code = (await api(`/api/children/${child.id}/pairing-code`, { method: 'POST', token })).data.code;
    const pair = await api('/api/pair', { method: 'POST', body: { code, platform: 'android', model: 'Demo' } });
    const dt = pair.data?.deviceToken;
    await api('/api/locations', {
      method: 'POST', token: dt,
      body: { points: [{ lat, lng, recorded_at: new Date().toISOString(), battery_level: battery }] },
    });
    await api('/api/device/status', { method: 'POST', token: dt, body: { battery_level: battery, is_charging: charging } });
    console.log(`• ${name}: ${battery}%${charging ? ' ⚡' : ''} @ ${lat},${lng}${battery <= 15 ? '  (low-battery alert)' : ''}`);
  }

  console.log('\n========================================');
  console.log('  DEMO FAMILY READY — log in as PARENT');
  console.log(`  Email:    ${EMAIL}`);
  console.log(`  Password: ${PASSWORD}`);
  console.log(`  Children: ${KIDS.map((k) => k[0]).join(', ')}`);
  console.log('========================================');
}

main().catch((e) => { console.error('seed failed:', e.message); process.exit(1); });
