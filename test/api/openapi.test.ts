import { describe, it, expect } from 'vitest';
import { GET as openapi } from '@/app/api/openapi.json/route';
import { GET as docs } from '@/app/api/docs/route';

describe('openapi', () => {
  it('serves a valid-ish spec covering core paths', async () => {
    const doc = await (await openapi(new Request('http://t/'))).json();
    expect(doc.openapi).toMatch(/^3\./);
    const publicPaths = [
      '/api/auth/register',
      '/api/auth/login',
      '/api/auth/refresh',
      '/api/auth/google',
      '/api/health',
      '/api/pair',
      '/api/locations',
      '/api/device/status',
      '/api/device/telemetry',
      '/api/device/app-usage',
      '/api/device/monitoring',
      '/api/device/monitors/{parentId}',
      '/api/device/monitors/{parentId}/messages',
      '/api/children',
      '/api/children/{id}',
      '/api/children/{id}/pairing-code',
      '/api/children/{id}/location/current',
      '/api/children/{id}/location/history',
      '/api/children/{id}/routes',
      '/api/children/{id}/alerts',
      '/api/children/{id}/messages',
      '/api/children/{id}/zones',
      '/api/children/{id}/zones/{zoneId}',
      '/api/children/{id}/app-usage',
      '/api/messages/summary',
      '/api/devices/{id}',
      '/api/parent/push-token',
      '/api/cron/offline-sweep',
      '/api/cron/location-retention',
    ];
    publicPaths.forEach((path) => expect(doc.paths[path], path).toBeDefined());
    expect(doc.components.securitySchemes.parentJwt).toBeDefined();
    expect(doc.components.securitySchemes.deviceToken).toBeDefined();
  });
  it('serves swagger html', async () => {
    const r = await docs(new Request('http://t/'));
    expect(r.headers.get('content-type')).toContain('text/html');
    expect(await r.text()).toContain('swagger-ui');
  });
});
