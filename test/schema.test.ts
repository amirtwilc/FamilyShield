import { describe, it, expect } from 'vitest';
import * as schema from '@/db/schema';

describe('schema', () => {
  it('exports all tables', () => {
    expect(schema.parents).toBeDefined();
    expect(schema.subscriptionTiers).toBeDefined();
    expect(schema.children).toBeDefined();
    expect(schema.childParentLinks).toBeDefined();
    expect(schema.devices).toBeDefined();
    expect(schema.pairingCodes).toBeDefined();
    expect(schema.locations).toBeDefined();
    expect(schema.safeZones).toBeDefined();
    expect(schema.alerts).toBeDefined();
  });
});
