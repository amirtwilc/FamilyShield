import { databaseLimiter, tooMany } from '@/lib/ratelimit';

const burst = databaseLimiter(120, 60_000);
const daily = databaseLimiter(2_000, 24 * 60 * 60_000);

/** Limits both short bursts and sustained storage/push abuse across all instances. */
export async function enforceChatSendLimit(senderKey: string): Promise<Response | null> {
  if (!(await burst.check(`chat:burst:${senderKey}`)).allowed) return tooMany();
  if (!(await daily.check(`chat:daily:${senderKey}`)).allowed) return tooMany();
  return null;
}
