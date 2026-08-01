import { describe, expect, it, vi } from 'vitest';
import { assignParentTier, parseTierAssignmentArgs } from '../../scripts/set-parent-tier.mjs';

describe('parent tier assignment command', () => {
  it('parses and normalizes a supported assignment', () => {
    expect(parseTierAssignmentArgs(['--email', ' Admin@Test.IO ', '--tier', 'ADMIN']))
      .toEqual({ email: 'admin@test.io', tier: 'admin' });
  });

  it('rejects unsupported tiers', () => {
    expect(() => parseTierAssignmentArgs(['--email', 'admin@test.io', '--tier', 'pro']))
      .toThrow('Tier must be either free or admin');
  });

  it('updates exactly one parent after validating the active tier', async () => {
    const query = vi.fn()
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ code: 'admin' }] })
      .mockResolvedValueOnce({
        rowCount: 1,
        rows: [{ id: 'parent-1', email: 'Admin@Test.io', tier_code: 'free' }],
      })
      .mockResolvedValueOnce({ rowCount: 1, rows: [] });

    await expect(assignParentTier({ query }, { email: 'admin@test.io', tier: 'admin' }))
      .resolves.toEqual({ email: 'Admin@Test.io', previousTier: 'free', tier: 'admin' });
    expect(query).toHaveBeenLastCalledWith(
      'UPDATE parents SET tier_code = $1 WHERE id = $2',
      ['admin', 'parent-1'],
    );
  });

  it('refuses missing or ambiguous parents', async () => {
    const query = vi.fn()
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ code: 'admin' }] })
      .mockResolvedValueOnce({ rowCount: 0, rows: [] });

    await expect(assignParentTier({ query }, { email: 'missing@test.io', tier: 'admin' }))
      .rejects.toThrow('Expected exactly one parent');
  });
});
