import { getAppCheck } from 'firebase-admin/app-check';
import { getAuth, type CreateRequest, type UpdateRequest } from 'firebase-admin/auth';
import { firebaseProjectId, getFirebaseAdminApp } from '@/lib/firebase/admin';

export type FirebaseIdentity = {
  uid: string;
  email: string;
  emailVerified: boolean;
  providerId: string | null;
  providerUid: string | null;
  authTime: number;
};

export type FirebaseAdminUser = {
  uid: string;
  email?: string;
};

export interface FirebaseAuthAdmin {
  verifyIdToken(token: string): Promise<FirebaseIdentity>;
  verifyAppCheck(token: string): Promise<void>;
  getUser(uid: string): Promise<FirebaseAdminUser>;
  getUserByEmail(email: string): Promise<FirebaseAdminUser>;
  createUser(properties: CreateRequest): Promise<FirebaseAdminUser>;
  updateUser(uid: string, properties: UpdateRequest): Promise<FirebaseAdminUser>;
  createCustomToken(uid: string): Promise<string>;
  revokeRefreshTokens(uid: string): Promise<void>;
}

class AdminSdkFirebaseAuth implements FirebaseAuthAdmin {
  async verifyIdToken(token: string): Promise<FirebaseIdentity> {
    const app = getFirebaseAdminApp();
    const decoded = await getAuth(app).verifyIdToken(token, true);
    if (decoded.aud !== firebaseProjectId()) throw new Error('wrong Firebase project');
    if (!decoded.email || typeof decoded.auth_time !== 'number') throw new Error('missing Firebase claims');
    const providerId = typeof decoded.firebase?.sign_in_provider === 'string'
      ? decoded.firebase.sign_in_provider
      : null;
    const providerUid = providerId === 'google.com' && typeof decoded.firebase?.identities?.['google.com']?.[0] === 'string'
      ? decoded.firebase.identities['google.com'][0]
      : null;
    return {
      uid: decoded.uid,
      email: decoded.email.trim().toLowerCase(),
      emailVerified: decoded.email_verified === true,
      providerId,
      providerUid,
      authTime: decoded.auth_time,
    };
  }

  async verifyAppCheck(token: string): Promise<void> {
    await getAppCheck(getFirebaseAdminApp()).verifyToken(token);
  }

  getUser(uid: string) { return getAuth(getFirebaseAdminApp()).getUser(uid); }
  getUserByEmail(email: string) { return getAuth(getFirebaseAdminApp()).getUserByEmail(email); }
  createUser(properties: CreateRequest) { return getAuth(getFirebaseAdminApp()).createUser(properties); }
  updateUser(uid: string, properties: UpdateRequest) {
    return getAuth(getFirebaseAdminApp()).updateUser(uid, properties);
  }
  createCustomToken(uid: string) { return getAuth(getFirebaseAdminApp()).createCustomToken(uid); }
  async revokeRefreshTokens(uid: string) {
    await getAuth(getFirebaseAdminApp()).revokeRefreshTokens(uid);
  }
}

let override: FirebaseAuthAdmin | null = null;
export function setFirebaseAuthAdmin(admin: FirebaseAuthAdmin) { override = admin; }
export function resetFirebaseAuthAdmin() { override = null; }
export function firebaseAuthAdmin(): FirebaseAuthAdmin { return override ?? new AdminSdkFirebaseAuth(); }

export function appCheckRequired(): boolean {
  const configured = process.env.FIREBASE_REQUIRE_APP_CHECK;
  if (configured !== undefined) return configured.toLowerCase() === 'true';
  return process.env.NODE_ENV === 'production';
}

export async function requireAppCheck(
  req: Request,
  required = appCheckRequired(),
): Promise<boolean> {
  if (!required) return true;
  const token = req.headers.get('x-firebase-appcheck');
  if (!token) return false;
  try {
    await firebaseAuthAdmin().verifyAppCheck(token);
    return true;
  } catch {
    return false;
  }
}

export function isFirebaseUserNotFound(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && 'code' in error
    && (error as { code?: unknown }).code === 'auth/user-not-found';
}
