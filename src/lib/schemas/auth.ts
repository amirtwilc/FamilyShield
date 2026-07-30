import { z } from 'zod';

const normalizedEmail = z.string().email().transform((value) => value.trim().toLowerCase());
export const registerSchema = z.object({ email: normalizedEmail, password: z.string().min(8) });
export const loginSchema = z.object({ email: normalizedEmail, password: z.string() });
export const refreshSchema = z.object({ refreshToken: z.string().min(1) });
export const googleSchema = z.object({ idToken: z.string().min(1) });
export const tokenPairSchema = z.object({ accessToken: z.string(), refreshToken: z.string() });
export const legacyMigrateSchema = z.object({
  email: z.string().email().transform((value) => value.trim().toLowerCase()),
  password: z.string().min(1).max(4096),
});
