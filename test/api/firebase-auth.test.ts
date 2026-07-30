import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import type { CreateRequest, UpdateRequest } from 'firebase-admin/auth';
import { POST as bootstrap } from '@/app/api/auth/bootstrap/route';
import { POST as migrate } from '@/app/api/auth/legacy-migrate/route';
import { POST as revoke } from '@/app/api/auth/revoke-sessions/route';
import { POST as legacyLogin } from '@/app/api/auth/login/route';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import {
  resetFirebaseAuthAdmin,
  setFirebaseAuthAdmin,
  type FirebaseAdminUser,
  type FirebaseAuthAdmin,
  type FirebaseIdentity,
} from '@/lib/auth/firebase';
import { hashPassword } from '@/lib/auth/password';
import { resetDb } from '../helpers/db';

class FakeFirebaseAdmin implements FirebaseAuthAdmin {
  identity: FirebaseIdentity = {
    uid: 'firebase-user',
    email: 'parent@example.com',
    emailVerified: true,
    providerId: 'password',
    providerUid: null,
    authTime: Math.floor(Date.now() / 1000),
  };
  users = new Map<string, FirebaseAdminUser>();
  appCheckValid = true;
  revoked: string[] = [];

  async verifyIdToken(token: string) {
    if (token === 'invalid') throw new Error('invalid token');
    return this.identity;
  }
  async verifyAppCheck() {
    if (!this.appCheckValid) throw new Error('bad app check');
  }
  async getUser(uid: string) {
    const user = this.users.get(uid);
    if (!user) throw Object.assign(new Error('missing'), { code: 'auth/user-not-found' });
    return user;
  }
  async getUserByEmail(email: string) {
    const user = [...this.users.values()].find((candidate) => candidate.email === email);
    if (!user) throw Object.assign(new Error('missing'), { code: 'auth/user-not-found' });
    return user;
  }
  async createUser(properties: CreateRequest) {
    const user = { uid: properties.uid!, email: properties.email };
    this.users.set(user.uid, user);
    return user;
  }
  async updateUser(uid: string, properties: UpdateRequest) {
    const current = await this.getUser(uid);
    const updated = { ...current, email: properties.email ?? current.email };
    this.users.set(uid, updated);
    return updated;
  }
  async createCustomToken(uid: string) { return `custom:${uid}`; }
  async revokeRefreshTokens(uid: string) { this.revoked.push(uid); }
}

const firebaseBearer = 'eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vZmFtaWx5c2hpZWxkIn0.signature';
const request = (token = firebaseBearer, body?: unknown, appCheck = true) => new Request('http://test/', {
  method: 'POST',
  headers: {
    authorization: `Bearer ${token}`,
    ...(appCheck ? { 'x-firebase-appcheck': 'valid-app' } : {}),
  },
  ...(body === undefined ? {} : { body: JSON.stringify(body) }),
});

let fake: FakeFirebaseAdmin;

beforeEach(async () => {
  await resetDb();
  fake = new FakeFirebaseAdmin();
  setFirebaseAuthAdmin(fake);
  delete process.env.FIREBASE_REQUIRE_APP_CHECK;
  delete process.env.LEGACY_AUTH_CUTOFF_AT;
});
afterEach(() => {
  resetFirebaseAuthAdmin();
  delete process.env.FIREBASE_REQUIRE_APP_CHECK;
  delete process.env.LEGACY_AUTH_CUTOFF_AT;
});

describe('Firebase parent authentication', () => {
  it('bootstraps a pre-provisioned user without changing the Neon parent id', async () => {
    const [parent] = await db.insert(parents).values({
      email: 'parent@example.com',
      passwordHash: 'legacy',
    }).returning();
    fake.identity.uid = parent.id;

    const response = await bootstrap(request());
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ parentId: parent.id });
    const [updated] = await db.select().from(parents).where(eq(parents.id, parent.id));
    expect(updated!.firebaseUid).toBe(parent.id);
  });

  it('creates a Neon parent only for a genuinely new verified Firebase user', async () => {
    fake.identity = { ...fake.identity, uid: 'new-firebase-user', email: 'new@example.com' };
    const response = await bootstrap(request());
    expect(response.status).toBe(200);
    const payload = await response.json();
    const [created] = await db.select().from(parents).where(eq(parents.firebaseUid, 'new-firebase-user'));
    expect(created!.id).toBe(payload.parentId);
    expect(created!.passwordHash).toBeNull();
  });

  it('refuses unverified email and identity-by-email merging', async () => {
    fake.identity.emailVerified = false;
    expect((await bootstrap(request())).status).toBe(403);

    fake.identity.emailVerified = true;
    fake.identity.uid = 'different-established-uid';
    await db.insert(parents).values({ email: fake.identity.email });
    const conflict = await bootstrap(request());
    expect(conflict.status).toBe(409);
  });

  it('migrates an Argon2 password once and clears the legacy hash after Firebase succeeds', async () => {
    const [parent] = await db.insert(parents).values({
      email: 'legacy@example.com',
      passwordHash: await hashPassword('OldPassword!1'),
    }).returning();
    fake.users.set(parent.id, { uid: parent.id, email: parent.email });

    const response = await migrate(request(firebaseBearer, {
      email: 'LEGACY@example.com',
      password: 'OldPassword!1',
    }));
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ customToken: `custom:${parent.id}` });
    const [updated] = await db.select().from(parents).where(eq(parents.id, parent.id));
    expect(updated!.passwordHash).toBeNull();
    expect(updated!.firebaseUid).toBe(parent.id);
    expect(updated!.authMigratedAt).toBeInstanceOf(Date);
  });

  it('returns the same generic migration error for an unknown email and a wrong password', async () => {
    await db.insert(parents).values({
      email: 'wrong-password@example.com',
      passwordHash: await hashPassword('RightPassword!1'),
    });
    const unknown = await migrate(request(firebaseBearer, {
      email: 'unknown@example.com',
      password: 'WrongPassword!1',
    }));
    const wrong = await migrate(request(firebaseBearer, {
      email: 'wrong-password@example.com',
      password: 'WrongPassword!1',
    }));
    expect(unknown.status).toBe(401);
    expect(wrong.status).toBe(401);
    expect(await unknown.json()).toEqual(await wrong.json());
  });

  it('rate limits repeated legacy migration attempts', async () => {
    let response!: Response;
    for (let attempt = 0; attempt < 6; attempt++) {
      response = await migrate(request(firebaseBearer, {
        email: 'rate-limited@example.com',
        password: 'WrongPassword!1',
      }));
    }
    expect(response.status).toBe(429);
  });

  it('disables legacy login after the configured transition cutoff', async () => {
    process.env.LEGACY_AUTH_CUTOFF_AT = '2000-01-01T00:00:00.000Z';
    const response = await legacyLogin(new Request('http://test/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: 'old@example.com', password: 'password' }),
    }));
    expect(response.status).toBe(426);
  });

  it('requires App Check for migration and Firebase parent requests when enabled', async () => {
    const migration = await migrate(request(firebaseBearer, {
      email: 'nobody@example.com',
      password: 'WrongPassword!1',
    }, false));
    expect(migration.status).toBe(401);

    process.env.FIREBASE_REQUIRE_APP_CHECK = 'true';
    const [parent] = await db.insert(parents).values({
      email: fake.identity.email,
      firebaseUid: fake.identity.uid,
    }).returning();
    expect(parent).toBeTruthy();
    expect((await revoke(request(firebaseBearer, undefined, false))).status).toBe(401);
    fake.appCheckValid = false;
    expect((await revoke(request())).status).toBe(401);
  });

  it('rejects invalid, unverified, and stale-auth tokens and revokes a recent session', async () => {
    await db.insert(parents).values({
      email: fake.identity.email,
      firebaseUid: fake.identity.uid,
    });
    expect((await revoke(request('invalid'))).status).toBe(401);

    fake.identity.emailVerified = false;
    expect((await revoke(request())).status).toBe(403);

    fake.identity.emailVerified = true;
    fake.identity.authTime = Math.floor(Date.now() / 1000) - 301;
    expect((await revoke(request())).status).toBe(403);

    fake.identity.authTime = Math.floor(Date.now() / 1000);
    expect((await revoke(request())).status).toBe(200);
    expect(fake.revoked).toEqual([fake.identity.uid]);
  });
});
