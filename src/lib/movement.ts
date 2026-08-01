export type MovementMode = 'stationary' | 'walking' | 'driving';

export const STATIONARY_MAX_SPEED_MPS = 0.5;
export const DRIVING_MIN_SPEED_MPS = 4.17;
export const SUBSTANTIAL_DRIVING_SEGMENT_M = 300;

export type InferredMovementSegment = {
  speedMps: number;
  distanceM: number;
};

export function movementModeForSpeed(speed: unknown): MovementMode | null {
  if (speed == null || speed === '') return null;
  const value = Number(speed);
  if (!Number.isFinite(value) || value < 0) return null;
  if (value <= STATIONARY_MAX_SPEED_MPS) return 'stationary';
  return value >= DRIVING_MIN_SPEED_MPS ? 'driving' : 'walking';
}

export function movementModeForTrip(input: {
  speeds: Array<number | null | undefined>;
  inferredSegments?: InferredMovementSegment[];
  distanceM: number;
  durationSeconds: number;
}): Exclude<MovementMode, 'stationary'> {
  const movingSpeeds = input.speeds
    .filter((speed): speed is number =>
      speed != null && Number.isFinite(speed) && speed > STATIONARY_MAX_SPEED_MPS,
    );
  const drivingSamples = movingSpeeds.filter((speed) => speed >= DRIVING_MIN_SPEED_MPS).length;
  const hasSustainedDriving = drivingSamples >= 2 ||
    (drivingSamples === 1 && movingSpeeds.length <= 2);
  const inferredMoving = (input.inferredSegments ?? [])
    .filter((segment) => Number.isFinite(segment.speedMps) && segment.speedMps > STATIONARY_MAX_SPEED_MPS);
  const inferredDriving = inferredMoving
    .filter((segment) => segment.speedMps >= DRIVING_MIN_SPEED_MPS);
  const hasInferredDriving = inferredDriving.length >= 2 ||
    inferredDriving.some((segment) => segment.distanceM >= SUBSTANTIAL_DRIVING_SEGMENT_M) ||
    (inferredDriving.length === 1 && inferredMoving.length <= 2);
  const averageRouteSpeed = input.durationSeconds > 0
    ? input.distanceM / input.durationSeconds
    : null;

  return hasSustainedDriving || hasInferredDriving ||
    (averageRouteSpeed != null && averageRouteSpeed >= DRIVING_MIN_SPEED_MPS)
    ? 'driving'
    : 'walking';
}
