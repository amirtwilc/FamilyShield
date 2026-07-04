import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import { ok, err } from '@/lib/http';
import { parseBody } from '@/lib/validate';
import { encodeCursor, decodeCursor } from '@/lib/pagination';

describe('http helpers', () => {
  it('ok wraps data', async () => {
    const r = ok({ a: 1 }, 201);
    expect(r.status).toBe(201);
    expect(await r.json()).toEqual({ a: 1 });
  });

  it('err uses the envelope', async () => {
    const r = err('bad', 'nope', 400, { field: 'x' });
    expect(r.status).toBe(400);
    expect(await r.json()).toEqual({ error: { code: 'bad', message: 'nope', details: { field: 'x' } } });
  });

  it('parseBody returns 400 on invalid', async () => {
    const req = new Request('http://t/', { method: 'POST', body: '{}' });
    const out = await parseBody(req, z.object({ x: z.string() }));
    expect('response' in out && out.response.status).toBe(400);
  });

  it('cursor round-trips', () => {
    const c = encodeCursor({ recordedAt: '2026-06-19T00:00:00Z', id: 'abc' });
    expect(decodeCursor(c)).toEqual({ recordedAt: '2026-06-19T00:00:00Z', id: 'abc' });
    expect(decodeCursor('garbage!!')).toBeNull();
  });
});
