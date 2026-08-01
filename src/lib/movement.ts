export type MovementMode = 'stationary' | 'walking' | 'driving';

export const STATIONARY_MAX_SPEED_MPS = 0.5;
export const DRIVING_MIN_SPEED_MPS = 4.17;

export function movementModeForSpeed(speed: unknown): MovementMode | null {
  if (speed == null || speed === '') return null;
  const value = Number(speed);
  if (!Number.isFinite(value) || value < 0) return null;
  if (value <= STATIONARY_MAX_SPEED_MPS) return 'stationary';
  return value >= DRIVING_MIN_SPEED_MPS ? 'driving' : 'walking';
}

export function movementModeForTrip(input: {
  speeds: Array<number | null | undefined>;
  distanceM: number;
  durationSeconds: number;
}): Exclude<MovementMode, 'stationary'> {
  const movingSpeeds = input.speeds
    .filter((speed): speed is number =>
      speed != null && Number.isFinite(speed) && speed > STATIONARY_MAX_SPEED_MPS,
    )
    .sort((a, b) => a - b);
  const representativeSpeed = movingSpeeds.length > 0
    ? movingSpeeds[Math.floor(movingSpeeds.length / 2)]
    : input.durationSeconds > 0
      ? input.distanceM / input.durationSeconds
      : null;

  return representativeSpeed != null && representativeSpeed >= DRIVING_MIN_SPEED_MPS
    ? 'driving'
    : 'walking';
}
