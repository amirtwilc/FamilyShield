import { applicationDefault, cert, getApp, getApps, initializeApp, type App } from 'firebase-admin/app';

function parseServiceAccount(raw: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (typeof parsed.private_key === 'string') {
      parsed.private_key = parsed.private_key.replace(/\\n/g, '\n');
    }
    return parsed;
  } catch (jsonError) {
    try {
      return parseServiceAccount(Buffer.from(raw, 'base64').toString('utf8'));
    } catch {
      throw new Error(`Firebase service-account configuration is invalid: ${String(jsonError)}`);
    }
  }
}

export function firebaseProjectId(): string {
  const configured = process.env.FIREBASE_PROJECT_ID
    ?? process.env.GCLOUD_PROJECT
    ?? process.env.GOOGLE_CLOUD_PROJECT;
  if (configured) return configured;

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (raw) {
    const projectId = parseServiceAccount(raw).project_id;
    if (typeof projectId === 'string' && projectId) return projectId;
  }
  throw new Error('FIREBASE_PROJECT_ID is required');
}

export function getFirebaseAdminApp(): App {
  if (getApps().length) return getApp();

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (raw) {
    return initializeApp({
      credential: cert(parseServiceAccount(raw)),
      projectId: firebaseProjectId(),
    });
  }

  // The Auth emulator needs no credential. Application Default Credentials are
  // used in managed production environments when JSON is not supplied.
  if (process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    return initializeApp({ projectId: firebaseProjectId() });
  }
  return initializeApp({ credential: applicationDefault(), projectId: firebaseProjectId() });
}
