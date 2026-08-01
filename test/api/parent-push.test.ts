import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import { POST, DELETE } from '@/app/api/parent/push-token/route';
import { db } from '@/db/client';
import { parentPushTokens } from '@/db/schema';
import { setSender, resetSender } from '@/lib/alerts/fcm';
import { notifyParentMessageFromChild } from '@/lib/messages/notifications';
import { signAccess } from '@/lib/auth/jwt';
import { resetDb } from '../helpers/db';
import { seedChild, seedParent } from '../helpers/factories';

beforeAll(async () => { await resetDb(); });
afterEach(() => resetSender());

const request = (method: string, token: string, fcmToken: string) => new Request('http://t/', {
  method,
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({ fcm_token: fcmToken }),
});

describe('parent push installations', () => {
  it('registers multiple phones and unregisters only the current token', async () => {
    const parent = await seedParent('multi-phone@test.io');
    await db.delete(parentPushTokens).where(eq(parentPushTokens.parentId, parent.id));
    const token = await signAccess(parent.id);

    expect((await POST(request('POST', token, 'phone-a'))).status).toBe(200);
    expect((await POST(request('POST', token, 'phone-b'))).status).toBe(200);
    expect((await db.select().from(parentPushTokens).where(eq(parentPushTokens.parentId, parent.id)))
      .map((row) => row.token).sort()).toEqual(['phone-a', 'phone-b']);

    expect((await DELETE(request('DELETE', token, 'phone-a'))).status).toBe(200);
    expect((await db.select().from(parentPushTokens).where(eq(parentPushTokens.parentId, parent.id)))
      .map((row) => row.token)).toEqual(['phone-b']);
  });

  it('delivers to every registered phone and prunes permanently invalid tokens', async () => {
    const parent = await seedParent('multi-delivery@test.io');
    const child = await seedChild(parent.id, 'Mia');
    await db.delete(parentPushTokens).where(eq(parentPushTokens.parentId, parent.id));
    await db.insert(parentPushTokens).values([
      { parentId: parent.id, token: 'invalid-phone' },
      { parentId: parent.id, token: 'valid-phone' },
    ]);
    const attempted: string[] = [];
    setSender({ async send(token) {
      attempted.push(token);
      if (token === 'invalid-phone') throw Object.assign(new Error('gone'), {
        code: 'messaging/registration-token-not-registered',
      });
      return true;
    } });

    expect(await notifyParentMessageFromChild(child.id, parent.id, 'message-1', 'Hello')).toBe(true);
    expect(attempted.sort()).toEqual(['invalid-phone', 'valid-phone']);
    expect((await db.select().from(parentPushTokens).where(eq(parentPushTokens.parentId, parent.id)))
      .map((row) => row.token)).toEqual(['valid-phone']);
  });
});
