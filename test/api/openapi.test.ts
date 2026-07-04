import { describe, it, expect } from 'vitest';
import { GET as openapi } from '@/app/api/openapi.json/route';
import { GET as docs } from '@/app/api/docs/route';

describe('openapi', () => {
  it('serves a valid-ish spec covering core paths', async () => {
    const doc = await (await openapi(new Request('http://t/'))).json();
    expect(doc.openapi).toMatch(/^3\./);
    expect(doc.paths['/api/auth/register']).toBeDefined();
    expect(doc.paths['/api/pair']).toBeDefined();
    expect(doc.paths['/api/locations']).toBeDefined();
    expect(doc.components.securitySchemes.parentJwt).toBeDefined();
    expect(doc.components.securitySchemes.deviceToken).toBeDefined();
  });
  it('serves swagger html', async () => {
    const r = await docs(new Request('http://t/'));
    expect(r.headers.get('content-type')).toContain('text/html');
    expect(await r.text()).toContain('swagger-ui');
  });
});
