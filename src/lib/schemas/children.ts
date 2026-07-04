import { z } from 'zod';

export const createChildSchema = z.object({ displayName: z.string().min(1).max(80) });
