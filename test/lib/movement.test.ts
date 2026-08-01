import { describe, expect, it } from 'vitest';
import { movementModeForSpeed, movementModeForTrip } from '@/lib/movement';

describe('movement classification', () => {
  it('classifies a current GPS speed using stationary and driving thresholds', () => {
    expect(movementModeForSpeed(null)).toBeNull();
    expect(movementModeForSpeed(0.5)).toBe('stationary');
    expect(movementModeForSpeed(1.4)).toBe('walking');
    expect(movementModeForSpeed(4.17)).toBe('driving');
  });

  it('recognizes a sustained driving leg even when most route samples are walking', () => {
    expect(movementModeForTrip({
      speeds: [12, 11, 1.3, 1.2, 1.4, 1.5, 1.1],
      distanceM: 3_000,
      durationSeconds: 1_800,
    })).toBe('driving');
  });

  it('ignores one isolated driving-speed sample during an otherwise walking route', () => {
    expect(movementModeForTrip({
      speeds: [1.2, 1.4, 12, 1.3, 1.5],
      distanceM: 1_500,
      durationSeconds: 1_200,
    })).toBe('walking');
  });

  it('uses impossible walking distance and time as driving evidence', () => {
    expect(movementModeForTrip({
      speeds: [1.2, 1.4, 1.3],
      distanceM: 6_000,
      durationSeconds: 1_200,
    })).toBe('driving');
  });

  it('uses coordinate and timestamp segments when reported GPS speeds are missing', () => {
    expect(movementModeForTrip({
      speeds: [],
      inferredSegments: [
        { speedMps: 9, distanceM: 1_100 },
        { speedMps: 1.3, distanceM: 300 },
        { speedMps: 1.2, distanceM: 250 },
      ],
      distanceM: 1_650,
      durationSeconds: 1_800,
    })).toBe('driving');
  });

  it('falls back to average trip speed when legacy points have no speed', () => {
    expect(movementModeForTrip({ speeds: [], distanceM: 1_000, durationSeconds: 600 })).toBe('walking');
    expect(movementModeForTrip({ speeds: [], distanceM: 10_000, durationSeconds: 600 })).toBe('driving');
  });
});
