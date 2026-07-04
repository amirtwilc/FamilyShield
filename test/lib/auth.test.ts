import { describe, it, expect } from 'vitest';
import { hashPassword, verifyPassword } from '@/lib/auth/password';
import { signAccess, verifyAccess } from '@/lib/auth/jwt';
import { createDeviceToken, hashToken } from '@/lib/auth/device';

describe('auth primitives', () => {
  it('hashes and verifies passwords', async () => {
    const h = await hashPassword('s3cret');
    expect(await verifyPassword(h, 's3cret')).toBe(true);
    expect(await verifyPassword(h, 'wrong')).toBe(false);
  });

  it('signs and verifies access tokens', async () => {
    const t = await signAccess('parent-123');
    expect(await verifyAccess(t)).toBe('parent-123');
    await expect(verifyAccess('not.a.jwt')).rejects.toThrow();
  });

  it('device token hash is deterministic and matches', () => {
    const { token, hash } = createDeviceToken();
    expect(hashToken(token)).toBe(hash);
    expect(hash).not.toBe(token);
  });
});
