/**
 * Reads a required secret/env var, throwing if it is missing or empty.
 *
 * Without this guard `process.env.JWT_SECRET!` evaluates to `undefined` when the
 * var is unset, and `new TextEncoder().encode(undefined)` silently produces the
 * bytes of the literal string "undefined" — a fully predictable signing key that
 * would let anyone forge access/refresh tokens. Failing closed at first use is
 * far safer than booting with a guessable secret.
 */
export function requireEnv(name: string): string {
  const v = process.env[name];
  if (!v || v.trim().length === 0) {
    throw new Error(`Required environment variable ${name} is not set`);
  }
  return v;
}
