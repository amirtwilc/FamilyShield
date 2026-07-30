import { beforeAll, describe, expect, it } from 'vitest';
import { getAuth } from 'firebase-admin/auth';
import { POST as bootstrap } from '@/app/api/auth/bootstrap/route';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { getFirebaseAdminApp } from '@/lib/firebase/admin';
import { resetDb } from '../helpers/db';

const emulatorHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const emulatorTest = emulatorHost ? describe : describe.skip;

type EmulatorAuthResponse = {
  localId: string;
  idToken: string;
  email: string;
};

async function authRequest(operation: 'signUp' | 'signInWithPassword', body: object) {
  const response = await fetch(
    `http://${emulatorHost}/identitytoolkit.googleapis.com/v1/accounts:${operation}?key=fake-api-key`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ ...body, returnSecureToken: true }),
    },
  );
  if (!response.ok) throw new Error(await response.text());
  return response.json() as Promise<EmulatorAuthResponse>;
}

emulatorTest('Firebase Auth Emulator contract', () => {
  beforeAll(async () => {
    process.env.FIREBASE_PROJECT_ID = 'demo-familyshield';
    await resetDb();
  });

  it('accepts a real emulator-issued verified ID token and bootstraps Neon', async () => {
    const email = `emulator-${Date.now()}@example.com`;
    const password = 'StrongPassword!1';
    const registered = await authRequest('signUp', { email, password });
    await getAuth(getFirebaseAdminApp()).updateUser(registered.localId, { emailVerified: true });
    const signedIn = await authRequest('signInWithPassword', { email, password });

    const response = await bootstrap(new Request('http://test/api/auth/bootstrap', {
      method: 'POST',
      headers: { authorization: `Bearer ${signedIn.idToken}` },
    }));

    expect(response.status).toBe(200);
    const payload = await response.json();
    const rows = await db.select().from(parents);
    expect(rows).toHaveLength(1);
    expect(rows[0]!.id).toBe(payload.parentId);
    expect(rows[0]!.firebaseUid).toBe(registered.localId);
    expect(rows[0]!.email).toBe(email);
  });
});
