import { err } from '@/lib/http';

export function legacyAuthEnabled(now = new Date()): boolean {
  const cutoff = process.env.LEGACY_AUTH_CUTOFF_AT;
  if (!cutoff) return true;
  const parsed = new Date(cutoff);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error('LEGACY_AUTH_CUTOFF_AT must be an ISO-8601 timestamp');
  }
  return now < parsed;
}

export function legacyAuthUnavailable(): Response {
  return err(
    'legacy_auth_expired',
    'This app version is no longer supported. Update FamilyShield to sign in.',
    426,
  );
}
