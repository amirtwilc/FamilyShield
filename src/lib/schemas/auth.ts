import { z } from 'zod';

export const registerSchema = z.object({ email: z.string().email(), password: z.string().min(8) });
export const loginSchema = z.object({ email: z.string().email(), password: z.string() });
export const refreshSchema = z.object({ refreshToken: z.string().min(1) });
export const googleSchema = z.object({ idToken: z.string().min(1) });
export const tokenPairSchema = z.object({ accessToken: z.string(), refreshToken: z.string() });
