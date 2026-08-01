import { describe, expect, it } from 'vitest';
import { movementModeForSpeed, movementModeForTrip } from '@/lib/movement';

describe('movement classification', () => {
  it('classifies a current GPS speed using stationary and driving thresholds', () => {
    expect(movementModeForSpeed(null)).toBeNull();
    expect(movementModeForSpeed(0.5)).toBe('stationary');
    expect(movementModeForSpeed(1.4)).toBe('walking');
    expect(movementModeForSpeed(4.17)).toBe('driving');
  });

  it('uses the median moving GPS speed for a trip and ignores stopped samples', () => {
    expect(movementModeForTrip({
      speeds: [0, 10, 12, 14],
      distanceM: 2_000,
      durationSeconds: 1_800,
    })).toBe('driving');
  });

  it('falls back to average trip speed when legacy points have no speed', () => {
    expect(movementModeForTrip({ speeds: [], distanceM: 1_000, durationSeconds: 600 })).toBe('walking');
    expect(movementModeForTrip({ speeds: [], distanceM: 10_000, durationSeconds: 600 })).toBe('driving');
  });
});
