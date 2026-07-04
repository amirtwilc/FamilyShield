import { z } from 'zod';
import { CHILD_AVATARS } from '@/lib/avatars';

export const createChildSchema = z.object({
  displayName: z.string().min(1).max(80),
  avatar: z.enum(CHILD_AVATARS).nullish().transform((value) => value ?? undefined),
});
