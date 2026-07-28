import { describe, expect, it } from 'vitest';
import { locationPoint } from '@/lib/schemas/locations';

describe('location timestamp validation', () => {
  it('accepts recent samples and rejects unreasonable replay/future timestamps', () => {
    const point = (recorded_at: string) => ({ lat: 50, lng: 6, recorded_at });
    expect(locationPoint.safeParse(point(new Date().toISOString())).success).toBe(true);
    expect(locationPoint.safeParse(point(new Date(Date.now() + 60 * 60_000).toISOString())).success).toBe(false);
    expect(locationPoint.safeParse(point(new Date(Date.now() - 120 * 24 * 60 * 60_000).toISOString())).success).toBe(false);
  });
});
