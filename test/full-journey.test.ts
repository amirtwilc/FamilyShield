import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from './helpers/db';

// End-to-end journey across EVERY feature behind the app's screens, exercised the
// way the parent + kid apps actually use the API: register → children → pairing →
// kid reports location/status/app-usage/chat → parent reads location, history,
// routes, zones, alerts, chat, app-usage → token refresh → security (IDOR / bad
// device token). Each section maps to a screen in the Android app.

import { POST as register } from '@/app/api/auth/register/route';
import { POST as refresh } from '@/app/api/auth/refresh/route';
import { GET as listChildren, POST as createChild } from '@/app/api/children/route';
import { POST as genCode } from '@/app/api/children/[id]/pairing-code/route';
import { POST as pair } from '@/app/api/pair/route';
import { GET as monitoring } from '@/app/api/device/monitoring/route';
import { POST as sendLocations } from '@/app/api/locations/route';
import { POST as sendStatus } from '@/app/api/device/status/route';
import { GET as currentLoc } from '@/app/api/children/[id]/location/current/route';
import { GET as history } from '@/app/api/children/[id]/location/history/route';
import { GET as routes } from '@/app/api/children/[id]/routes/route';
import { GET as alerts } from '@/app/api/children/[id]/alerts/route';
import { POST as createZone, GET as listZones } from '@/app/api/children/[id]/zones/route';
import { DELETE as delZone } from '@/app/api/children/[id]/zones/[zoneId]/route';
import { GET as parentMsgs, POST as parentSend } from '@/app/api/children/[id]/messages/route';
import { GET as kidMsgs, POST as kidSend } from '@/app/api/device/messages/route';
import { POST as kidUsage } from '@/app/api/device/app-usage/route';
import { GET as parentUsage } from '@/app/api/children/[id]/app-usage/route';

// --- tiny request helpers ---
const post = (body: unknown, token?: string) => new Request('http://t/', {
  method: 'POST',
  headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
  body: JSON.stringify(body),
});
const get = (query = '', token?: string) => new Request('http://t/' + query, {
  headers: token ? { authorization: `Bearer ${token}` } : {},
});
const del = (token: string) => new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${token}` } });
const ctx = (id: string) => ({ params: Promise.resolve({ id }) });
const ctx2 = (id: string, zoneId: string) => ({ params: Promise.resolve({ id, zoneId }) });
const J = async (r: Response) => ({ status: r.status, body: await r.json() });

const email = () => `journey_${Date.now()}_${Math.floor(Math.random() * 1e5)}@fs.test`;

beforeAll(async () => { await resetDb(); });

describe('full parent + kid journey (all features)', () => {
  it('walks every feature end to end', async () => {
    // 1) AUTH — parent registers (Login screen)
    const reg = await J(await register(post({ email: email(), password: 'SuperSecret123!' })));
    expect(reg.status).toBe(201);
    let accessToken = reg.body.accessToken as string;
    const refreshToken = reg.body.refreshToken as string;
    expect(accessToken).toBeTruthy();

    // 2) CHILDREN — add two (Settings / dashboard chips)
    expect((await createChild(post({ displayName: 'Mia' }, accessToken))).status).toBe(201);
    const noah = await J(await createChild(post({ displayName: 'Noah' }, accessToken)));
    expect(noah.status).toBe(201);
    const kids = (await J(await listChildren(get('', accessToken)))).body.children;
    expect(kids).toHaveLength(2);
    const mia = kids.find((c: any) => c.displayName === 'Mia');

    // 3) PAIRING — code + kid device pairs (Connect screen)
    const code = (await J(await genCode(post({}, accessToken), ctx(mia.id)))).body.code as string;
    expect(code).toMatch(/^\d{6}$/);
    const paired = await J(await pair(post({ code, platform: 'android', model: 'Pixel' })));
    expect(paired.status).toBe(201);
    const deviceToken = paired.body.deviceToken as string;
    expect(paired.body.childId).toBe(mia.id);          // the kid peered to the right child
    // A wrong code, and re-using the consumed code, are both rejected.
    expect((await pair(post({ code: '000000', platform: 'android' }))).status).toBe(400);
    expect((await pair(post({ code, platform: 'android' }))).status).toBe(400);

    // 4) LOCATION — kid reports a home→school path (Map + History + Routes screens)
    const base = new Date(); base.setUTCHours(12, 0, 0, 0);
    const at = (min: number) => new Date(base.getTime() + min * 60_000).toISOString();
    const day = base.toISOString().slice(0, 10);
    const HOME = { lat: 32.00, lng: 34.00 }; const SCHOOL = { lat: 32.02, lng: 34.00 };
    const points = [
      ...[0, 2, 4, 6].map((m) => ({ ...HOME, recorded_at: at(m), battery_level: 80 })),
      ...[20, 22, 24, 26].map((m) => ({ ...SCHOOL, recorded_at: at(m), battery_level: 70 })),
    ];
    expect((await sendLocations(post({ points }, deviceToken))).status).toBe(200);

    // current location → most recent (school)
    const cur = (await J(await currentLoc(get('', accessToken), ctx(mia.id)))).body;
    expect(cur.lat).toBeCloseTo(32.02, 2);

    // history for the day → all 8 points
    const hist = (await J(await history(get(`?date=${day}`, accessToken), ctx(mia.id)))).body;
    expect(hist.points.length).toBeGreaterThanOrEqual(8);

    // routes → at least one trip between two detected stops
    const rts = (await J(await routes(get('', accessToken), ctx(mia.id)))).body;
    expect(rts.stops.length).toBeGreaterThanOrEqual(2);
    expect(rts.trips.length).toBeGreaterThanOrEqual(1);

    // 5) STATUS / ALERTS — low battery raises an alert (dashboard Recent Alerts)
    expect((await sendStatus(post({ battery_level: 10, is_charging: false }, deviceToken))).status).toBe(200);
    const al = (await J(await alerts(get('', accessToken), ctx(mia.id)))).body.alerts;
    expect(al.some((a: any) => a.type === 'low_battery')).toBe(true);

    // The parent now sees the kid as a paired, online peer (device + last-seen + battery).
    const after = (await J(await listChildren(get('', accessToken)))).body.children;
    const miaPeer = after.find((c: any) => c.id === mia.id);
    expect(miaPeer.devices).toHaveLength(1);
    expect(miaPeer.devices[0].lastSeenAt).toBeTruthy();
    expect(miaPeer.devices[0].batteryLevel).toBe(10);

    // 6) SAFE ZONES — create, list, delete (Zones screen)
    const zone = (await J(await createZone(post({ name: 'School', lat: 32.02, lng: 34.00, radiusM: 200 }, accessToken), ctx(mia.id)))).body;
    expect((await J(await listZones(get('', accessToken), ctx(mia.id)))).body.zones).toHaveLength(1);
    expect((await delZone(del(accessToken), ctx2(mia.id, zone.id))).status).toBe(200);
    expect((await J(await listZones(get('', accessToken), ctx(mia.id)))).body.zones).toHaveLength(0);

    // 7) CHAT — both directions + read receipts (Chat screen)
    expect((await parentSend(post({ body: 'Where are you?' }, accessToken), ctx(mia.id))).status).toBe(201);
    const kidView = (await J(await kidMsgs(get('', deviceToken)))).body.messages; // marks parent msg read
    expect(kidView.map((m: any) => m.body)).toContain('Where are you?');
    expect((await kidSend(post({ body: 'At school!' }, deviceToken))).status).toBe(201);
    const parentView = (await J(await parentMsgs(get('?markRead=1', accessToken), ctx(mia.id)))).body.messages;
    expect(parentView.map((m: any) => m.body)).toEqual(['Where are you?', 'At school!']);
    expect(parentView.find((m: any) => m.sender === 'child').read_at).not.toBeNull();

    // 8) APP USAGE — kid reports, parent reads the breakdown (App Usage screen)
    expect((await kidUsage(post({ items: [
      { app: 'YouTube', category: 'Entertainment', minutes: 80 },
      { app: 'Roblox', category: 'Games', minutes: 45 },
    ] }, deviceToken))).status).toBe(200);
    const usage = (await J(await parentUsage(get('', accessToken), ctx(mia.id)))).body;
    expect(usage.totalTodayMin).toBe(125);
    expect(usage.apps[0]).toMatchObject({ app: 'YouTube', min: 80 });
    expect(usage.week).toHaveLength(7);

    // 9) SECOND PARENT - normal parent flow creates a placeholder child/code;
    // the already-paired kid device redeems it to add that parent to Mia.
    const regCo = await J(await register(post({ email: email(), password: 'CoParent123!' })));
    const coToken = regCo.body.accessToken as string;
    const coPlaceholder = await J(await createChild(post({ displayName: 'Mimi' }, coToken)));
    const coCode = (await J(await genCode(post({}, coToken), ctx(coPlaceholder.body.id)))).body.code as string;
    const addParent = await J(await pair(post({ code: coCode, platform: 'android', model: 'Pixel' }, deviceToken)));
    expect(addParent.status).toBe(200);
    expect(addParent.body.monitors).toHaveLength(2);

    const coKids = (await J(await listChildren(get('', coToken)))).body.children;
    expect(coKids).toHaveLength(1);
    expect(coKids[0]).toMatchObject({ id: mia.id, displayName: 'Mimi' });
    expect((await currentLoc(get('', coToken), ctx(mia.id))).status).toBe(200);
    expect((await J(await monitoring(get('', deviceToken)))).body.monitors).toHaveLength(2);

    // 10) TOKEN REFRESH — refresh token mints a working access token
    const refreshed = (await J(await refresh(post({ refreshToken })))).body;
    expect(refreshed.accessToken).toBeTruthy();
    expect((await listChildren(get('', refreshed.accessToken))).status).toBe(200);

    // 11) SECURITY — another parent can't read this child; bad device token rejected
    const reg2 = await J(await register(post({ email: email(), password: 'AnotherPass123!' })));
    expect((await currentLoc(get('', reg2.body.accessToken), ctx(mia.id))).status).toBe(404);
    expect((await sendLocations(post({ points }, 'not-a-real-device-token'))).status).toBe(401);
    expect((await listChildren(get('', 'garbage-access-token'))).status).toBe(401);
  });
});
