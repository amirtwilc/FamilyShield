import { describe, it, expect } from 'vitest';
import { requireEnv } from '@/lib/env';

describe('requireEnv', () => {
  it('returns the value when the var is set', () => {
    process.env.__FS_TEST_SECRET = 'a-real-secret';
    expect(requireEnv('__FS_TEST_SECRET')).toBe('a-real-secret');
  });

  it('throws instead of silently returning undefined when unset', () => {
    delete process.env.__FS_TEST_MISSING;
    expect(() => requireEnv('__FS_TEST_MISSING')).toThrow(/not set/);
  });

  it('treats whitespace-only as unset', () => {
    process.env.__FS_TEST_BLANK = '   ';
    expect(() => requireEnv('__FS_TEST_BLANK')).toThrow();
  });
});
