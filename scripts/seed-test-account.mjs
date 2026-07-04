// Seeds a ready-to-use test account against the running backend so you can log
// into the app immediately and see populated data (a child + a paired device
// reporting a location). Idempotent: re-running reuses the existing account/child.
//
//   node scripts/seed-test-account.mjs
//
// Credentials are printed at the end. Backend must be running (npm run dev).

const BASE = process.env.SEED_BASE_URL || 'http://localhost:3000';
const EMAIL = process.env.SEED_EMAIL || 'test@familyshield.app';
const PASSWORD = process.env.SEED_PASSWORD || 'Test1234';
const CHILD_NAME = 'Test Kid';
// A recognizable spot (central Tel Aviv) so the map shows something.
const LAT = 32.0853, LNG = 34.7818, BATTERY = 80;

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  return { status: res.status, data };
}

async function main() {
  // 1. Register, or log in if the account already exists.
  let reg = await api('/api/auth/register', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  if (reg.status === 409) {
    console.log('• account already exists — logging in');
    reg = await api('/api/auth/login', { method: 'POST', body: { email: EMAIL, password: PASSWORD } });
  } else if (reg.status === 201) {
    console.log('• created parent account');
  }
  const token = reg.data?.accessToken;
  if (!token) throw new Error('could not obtain token: ' + JSON.stringify(reg.data));

  // 2. Reuse an existing child or create one.
  const list = await api('/api/children', { token });
  let child = list.data?.children?.[0];
  if (!child) {
    const created = await api('/api/children', { method: 'POST', body: { displayName: CHILD_NAME }, token });
    child = created.data;
    console.log(`• created child "${child.displayName}"`);
  } else {
    console.log(`• reusing child "${child.displayName}"`);
  }

  // 3. Pair a (virtual) device and report a location + battery, so the dashboard
  //    shows the child online with a location on the map.
  const code = (await api(`/api/children/${child.id}/pairing-code`, { method: 'POST', token })).data.code;
  const pair = await api('/api/pair', { method: 'POST', body: { code, platform: 'android', model: 'Seed Device' } });
  const deviceToken = pair.data?.deviceToken;
  if (deviceToken) {
    await api('/api/locations', {
      method: 'POST', token: deviceToken,
      body: { points: [{ lat: LAT, lng: LNG, recorded_at: new Date().toISOString(), battery_level: BATTERY }] },
    });
    await api('/api/device/status', { method: 'POST', token: deviceToken, body: { battery_level: BATTERY, is_charging: false } });
    console.log('• paired a device and reported a location + battery');
  }

  console.log('\n========================================');
  console.log('  TEST ACCOUNT READY — log in as PARENT');
  console.log('========================================');
  console.log(`  Email:    ${EMAIL}`);
  console.log(`  Password: ${PASSWORD}`);
  console.log(`  Child:    ${child.displayName} (online, ${BATTERY}% battery, location set)`);
  console.log('========================================');
}

main().catch((e) => { console.error('seed failed:', e.message); process.exit(1); });
