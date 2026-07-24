export function isDeviceOnline(
  device: { lastSeenAt: Date | string | null; revokedAt: Date | string | null },
  nowMs = Date.now(),
  thresholdMin = Number(process.env.OFFLINE_THRESHOLD_MIN ?? 30),
): boolean {
  if (device.revokedAt != null || device.lastSeenAt == null) return false;
  return new Date(device.lastSeenAt).getTime() >= nowMs - thresholdMin * 60_000;
}
