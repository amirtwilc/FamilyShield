// Builds a full, realistic demo dataset under a single test account:
// several children, each with a paired device, a current location, safe zones,
// a few days of movement history (so routes + history + distance populate), and
// battery/charging state (one low enough to raise a low-battery alert).
//
//   node scripts/seed-demo.mjs
//
// Backend must be running (docker compose up, or npm run dev).

const BASE = process.env.SEED_BASE_URL || 'http://localhost:3000';
const EMAIL = process.env.SEED_EMAIL || 'parent@familyshield.app';
const PASSWORD = process.env.SEED_PASSWORD || 'Parent2026';

// Shared family home + common places (central Tel Aviv).
const HOME = { lat: 32.0780, lng: 34.7740, name: 'Home' };
const SCHOOL = { lat: 32.0850, lng: 34.7900, name: 'School' };
const PARK = { lat: 32.0905, lng: 34.7780, name: 'Park' };
const GRANDPARENTS = { lat: 32.0690, lng: 34.7710, name: 'Grandparents' };

const KIDS = [
  { name: 'Mia',   dest: SCHOOL,       battery: 78, charging: false, zones: [HOME, SCHOOL] },
  { name: 'Noah',  dest: SCHOOL,       battery: 13, charging: false, zones: [HOME, SCHOOL] }, // low battery -> alert
  { name: 'Lily',  dest: GRANDPARENTS, battery: 64, charging: true,  zones: [HOME, GRANDPARENTS] },
  { name: 'Ethan', dest: PARK,         battery: 41, charging: false, zones: [HOME, PARK] },
];

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const t = await res.text();
  return { status: res.status, data: t ? JSON.parse(t) : null };
}

// 3 days of home -> dest -> home movement; returns location points (oldest first).
function historyFor(dest) {
  const pts = [];
  const now = Date.now();
  for (let day = 3; day >= 1; day--) {
    let t = now - day * 86_400_000 + 7 * 3_600_000; // ~07:00 that day
    const push = (p) => pts.push({ lat: p.lat, lng: p.lng, recorded_at: new Date(t).toISOString() });
    const stay = (p, mins) => { for (let m = 0; m < mins; m += 3) { push(p); t += 180_000; } };
    const move = (a, b, mins) => { for (let i = 1; i <= 5; i++) { const f = i / 5; push({ lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f }); t += (mins / 5) * 60_000; } };
    stay(HOME, 18); move(HOME, dest, 20); stay(dest, 18); move(dest, HOME, 20);
  }
  // Final "current" position = at the destination, just now.
  pts.push({ lat: dest.lat, lng: dest.lng, recorded_at: new Date(now - 120_000).toISOString() });
  return pts;
}

async function main() {
  let auth = await api('/api/auth/register', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  if (auth.status === 409) auth = await api('/api/auth/login', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  const token = auth.data?.accessToken;
  if (!token) throw new Error('no token: ' + JSON.stringify(auth.data));

  const existing = (await api('/api/children', { token })).data?.children ?? [];
  const have = new Map(existing.map((c) => [c.displayName, c]));

  for (const kid of KIDS) {
    let child = have.get(kid.name);
    if (!child) {
      child = (await api('/api/children', { method: 'POST', body: { displayName: kid.name }, token })).data;
      console.log(`• created ${kid.name}`);
    } else {
      console.log(`• reusing ${kid.name}`);
    }

    // Pair a device and replay the movement history + final battery/status.
    const code = (await api(`/api/children/${child.id}/pairing-code`, { method: 'POST', token })).data.code;
    const dt = (await api('/api/pair', { method: 'POST', body: { code, platform: 'android', model: 'DemoSeed' } })).data.deviceToken;
    const pts = historyFor(kid.dest);
    for (let i = 0; i < pts.length; i += 180) {
      await api('/api/locations', { method: 'POST', token: dt, body: { points: pts.slice(i, i + 180).map((p) => ({ ...p, battery_level: kid.battery })) } });
    }
    await api('/api/device/status', { method: 'POST', token: dt, body: { battery_level: kid.battery, is_charging: kid.charging } });

    // Safe zones (skip any that already exist by name).
    const existingZones = ((await api(`/api/children/${child.id}/zones`, { token })).data?.zones ?? []).map((z) => z.name);
    for (const z of kid.zones) {
      if (existingZones.includes(z.name)) continue;
      await api(`/api/children/${child.id}/zones`, { method: 'POST', token, body: { name: z.name, lat: z.lat, lng: z.lng, radiusM: z.name === 'Home' ? 200 : 300 } });
    }

    const routes = (await api(`/api/children/${child.id}/routes`, { token })).data;
    console.log(`   ${kid.battery}%${kid.charging ? ' ⚡' : ''} · ${kid.zones.length} zones · ${routes.frequent.length} routes${kid.battery <= 15 ? ' · LOW-BATTERY ALERT' : ''}`);
  }

  console.log('\n========================================');
  console.log('  TEST ACCOUNT READY');
  console.log(`  Email:    ${EMAIL}`);
  console.log(`  Password: ${PASSWORD}`);
  console.log(`  Children: ${KIDS.map((k) => k.name).join(', ')}`);
  console.log('  Each has live location, safe zones, history & routes.');
  console.log('========================================');
}

main().catch((e) => { console.error('seed-demo failed:', e.message); process.exit(1); });
