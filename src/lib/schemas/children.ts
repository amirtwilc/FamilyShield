import { z } from 'zod';
import { CHILD_AVATARS } from '@/lib/avatars';

const phoneNumberSchema = z.union([z.string().max(40), z.null()]).optional()
  .transform((value) => {
    if (typeof value !== 'string') return value;
    const trimmed = value.trim();
    return trimmed === '' ? null : trimmed;
  });

export const createChildSchema = z.object({
  displayName: z.string().min(1).max(80),
  avatar: z.enum(CHILD_AVATARS).nullish().transform((value) => value ?? undefined),
  phoneNumber: phoneNumberSchema,
});
