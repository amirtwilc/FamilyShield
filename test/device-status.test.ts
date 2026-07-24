import { describe, expect, test } from 'vitest';
import { isDeviceOnline } from '@/lib/device-status';

describe('device online status', () => {
  const now = Date.parse('2026-07-24T12:00:00Z');

  test('treats a recently seen non-revoked device as online', () => {
    expect(isDeviceOnline({ lastSeenAt: '2026-07-24T11:45:00Z', revokedAt: null }, now, 30)).toBe(true);
  });

  test('treats a stale device as offline', () => {
    expect(isDeviceOnline({ lastSeenAt: '2026-07-24T11:29:00Z', revokedAt: null }, now, 30)).toBe(false);
  });

  test('treats revoked or never-seen devices as offline', () => {
    expect(isDeviceOnline({ lastSeenAt: '2026-07-24T11:59:00Z', revokedAt: '2026-07-24T11:59:30Z' }, now, 30)).toBe(false);
    expect(isDeviceOnline({ lastSeenAt: null, revokedAt: null }, now, 30)).toBe(false);
  });
});
