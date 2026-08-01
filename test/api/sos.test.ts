import { describe, it, expect, beforeAll, beforeEach, afterEach } from 'vitest';
import { eq } from 'drizzle-orm';
import { POST as startSos } from '@/app/api/device/sos/start/route';
import { POST as sosLocation } from '@/app/api/device/sos/location/route';
import { GET as parentCurrentSos } from '@/app/api/children/[id]/sos/current/route';
import { POST as urgentAlert } from '@/app/api/children/[id]/urgent-alert/route';
import { db } from '@/db/client';
import { alerts, childParentLinks, devices, messages, sosDailyUsage, sosEvents } from '@/db/schema';
import { setSender, resetSender, type PushOptions } from '@/lib/alerts/fcm';
import { signAccess } from '@/lib/auth/jwt';
import { resetDb } from '../helpers/db';
import { seedChild, seedDevice, seedParent } from '../helpers/factories';

const postDevice = (token: string, body: unknown) => new Request('http://t/', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});
const postParent = (token: string, body: unknown) => new Request('http://t/', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});
const parentGet = (token: string) => new Request('http://t/', { headers: { authorization: `Bearer ${token}` } });
const ctx = (id: string) => ({ params: Promise.resolve({ id }) });

describe('kid SOS and urgent alerts', () => {
  const pushes: Array<{ token: string; title: string; body: string; data?: Record<string, string>; options?: PushOptions }> = [];
  beforeAll(async () => { await resetDb(); });
  beforeEach(() => {
    pushes.length = 0;
    setSender({ async send(token, title, body, data, options) { pushes.push({ token, title, body, data, options }); return true; } });
  });
  afterEach(() => {
    resetSender();
    delete process.env.SOS_HIGH_RATE_DAILY_SECONDS;
    delete process.env.SOS_HIGH_RATE_INTERVAL_SECONDS;
  });

  it('starts SOS once and pushes urgent alerts to all linked parents', async () => {
    const p1 = await seedParent('sos-p1@test.io');
    const p2 = await seedParent('sos-p2@test.io');
    const c = await seedChild(p1.id, 'Mia');
    await db.insert(childParentLinks).values({ childId: c.id, parentId: p2.id, displayName: 'Mia' });
    const { token: deviceToken } = await seedDevice(c.id);
    const parentToken = await signAccess(p1.id);

    const r = await startSos(postDevice(deviceToken, {
      timezone: 'Asia/Jerusalem',
      local_day: '2026-07-26',
      location: { lat: 32.1, lng: 34.8, recorded_at: new Date(Date.now() - 60_000).toISOString(), battery_level: 77 },
    }));

    expect(r.status).toBe(201);
    const state = await r.json();
    expect(state.active).toBe(true);
    expect(state.highRateIntervalSeconds).toBe(60);
    expect(pushes).toHaveLength(2);
    expect(pushes.map((p) => p.token).sort()).toEqual([p1.fcmToken, p2.fcmToken].sort());
    expect(pushes[0].data).toEqual(expect.objectContaining({ type: 'kid_sos_started', priority: 'urgent', childId: c.id, childName: 'Mia' }));
    expect(pushes[0].options?.includeNotification).toBe(false);

    const again = await startSos(postDevice(deviceToken, { timezone: 'Asia/Jerusalem', local_day: '2026-07-26' }));
    expect(again.status).toBe(201);
    expect(pushes).toHaveLength(2);

    const current = await parentCurrentSos(parentGet(parentToken), ctx(c.id));
    const currentBody = await current.json();
    expect(current.status).toBe(200);
    expect(currentBody.active).toBe(true);
    expect(currentBody.event.lastLocation).toEqual({ lat: 32.1, lng: 34.8 });

    const rows = await db.select().from(alerts).where(eq(alerts.childId, c.id));
    expect(rows.filter((a) => a.type === 'kid_sos_started')).toHaveLength(2);
  });

  it('creates only one active SOS under concurrent start requests', async () => {
    const p = await seedParent('sos-concurrent@test.io');
    const c = await seedChild(p.id, 'Mia');
    const { token } = await seedDevice(c.id);
    const body = { timezone: 'UTC', local_day: '2026-07-28' };

    const responses = await Promise.all([
      startSos(postDevice(token, body)),
      startSos(postDevice(token, body)),
    ]);

    expect(responses.every((response) => response.status === 201)).toBe(true);
    expect(await db.select().from(sosEvents).where(eq(sosEvents.childId, c.id))).toHaveLength(1);
    expect(pushes).toHaveLength(1);
  });

  it('charges SOS high-rate location in 60 second units and caps daily use', async () => {
    process.env.SOS_HIGH_RATE_DAILY_SECONDS = '120';
    process.env.SOS_HIGH_RATE_INTERVAL_SECONDS = '60';
    const p = await seedParent('sos-quota@test.io');
    const c = await seedChild(p.id, 'Liam');
    const { token: deviceToken } = await seedDevice(c.id);

    await startSos(postDevice(deviceToken, { timezone: 'UTC', local_day: '2026-07-26' }));
    const point = (minute: number) => ({
      timezone: 'UTC',
      local_day: '2026-07-26',
      location: {
        lat: 31 + minute,
        lng: 35,
        recorded_at: new Date(Date.now() - (10 - minute) * 60_000).toISOString(),
        battery_level: 80,
      },
    });

    const first = await (await sosLocation(postDevice(deviceToken, point(1)))).json();
    const second = await (await sosLocation(postDevice(deviceToken, point(2)))).json();
    const third = await (await sosLocation(postDevice(deviceToken, point(3)))).json();

    expect(first.accepted).toBe(true);
    expect(first.state.remainingSeconds).toBe(60);
    expect(second.accepted).toBe(true);
    expect(second.state.remainingSeconds).toBe(0);
    expect(third.accepted).toBe(false);
    expect(third.reason).toBe('quota_exhausted');
  });

  it('serializes concurrent SOS quota charges', async () => {
    process.env.SOS_HIGH_RATE_DAILY_SECONDS = '60';
    process.env.SOS_HIGH_RATE_INTERVAL_SECONDS = '60';
    const p = await seedParent('sos-concurrent-quota@test.io');
    const c = await seedChild(p.id, 'Liam');
    const { token } = await seedDevice(c.id);
    await startSos(postDevice(token, { timezone: 'UTC', local_day: '2026-07-28' }));
    const request = (minute: number) => postDevice(token, {
      timezone: 'UTC',
      local_day: '2026-07-28',
      location: {
        lat: 31 + minute,
        lng: 35,
        recorded_at: new Date(Date.now() - minute * 60_000).toISOString(),
        battery_level: 80,
      },
    });

    const results = await Promise.all([
      sosLocation(request(1)).then((response) => response.json()),
      sosLocation(request(2)).then((response) => response.json()),
    ]);

    expect(results.filter((result) => result.accepted)).toHaveLength(1);
    const [usage] = await db.select().from(sosDailyUsage).where(eq(sosDailyUsage.childId, c.id));
    expect(usage.usedSeconds).toBe(60);
  });

  it('stores urgent parent alerts as urgent messages and applies cooldown', async () => {
    const p = await seedParent('urgent-alert@test.io');
    const c = await seedChild(p.id, 'Noa');
    const { device } = await seedDevice(c.id);
    await db.update(devices).set({ fcmToken: 'kid-fcm' }).where(eq(devices.id, device.id));
    const token = await signAccess(p.id);

    const r = await urgentAlert(postParent(token, { body: 'Please call me now' }), ctx(c.id));
    expect(r.status).toBe(201);
    expect((await r.json()).message.priority).toBe('urgent');
    expect(pushes).toEqual([expect.objectContaining({
      token: 'kid-fcm',
      data: expect.objectContaining({ type: 'urgent_alert', priority: 'urgent', childId: c.id, body: 'Please call me now' }),
      options: expect.objectContaining({ includeNotification: false }),
    })]);

    const rows = await db.select().from(messages).where(eq(messages.childId, c.id));
    expect(rows[0].priority).toBe('urgent');

    const blocked = await urgentAlert(postParent(token, { body: 'Again' }), ctx(c.id));
    expect(blocked.status).toBe(429);
  });
});
