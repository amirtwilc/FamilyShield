export interface PushSender {
  send(fcmToken: string, title: string, body: string, data?: Record<string, string>, options?: PushOptions): Promise<boolean>;
}

export type PushOptions = {
  includeNotification?: boolean;
  android?: {
    priority?: 'normal' | 'high';
    ttlMs?: number;
    channelId?: string;
    notificationPriority?: 'default' | 'high' | 'max';
    tag?: string;
  };
};

let override: PushSender | null = null;
export function setSender(s: PushSender) { override = s; }
export function resetSender() { override = null; }

function describeError(error: unknown): string {
  if (error instanceof Error) return error.message;
  return String(error);
}

function serviceAccountFromEnv(): Record<string, unknown> | null {
  const raw = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;

  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(raw);
  } catch (jsonError) {
    try {
      parsed = JSON.parse(Buffer.from(raw, 'base64').toString('utf8'));
    } catch {
      throw new Error(`FCM_SERVICE_ACCOUNT_JSON is not valid JSON: ${describeError(jsonError)}`);
    }
  }

  if (typeof parsed.private_key === 'string') {
    parsed.private_key = parsed.private_key.replace(/\\n/g, '\n');
  }
  if (typeof parsed.client_email !== 'string' || typeof parsed.private_key !== 'string') {
    throw new Error('FCM_SERVICE_ACCOUNT_JSON must be a Firebase service account private-key JSON');
  }
  return parsed;
}

class FirebaseSender implements PushSender {
  async send(fcmToken: string, title: string, body: string, data?: Record<string, string>, options?: PushOptions) {
    const serviceAccount = serviceAccountFromEnv();
    if (!serviceAccount) {
      console.warn('[push] FCM_SERVICE_ACCOUNT_JSON is not set; skipping push send');
      return false;
    }
    const [app, messaging] = await Promise.all([
      import('firebase-admin/app'),
      import('firebase-admin/messaging'),
    ]);
    if (!app.getApps().length) {
      app.initializeApp({ credential: app.cert(serviceAccount) });
    }
    const includeNotification = options?.includeNotification ?? true;
    const android = options?.android ? {
      ...(options.android.priority ? { priority: options.android.priority } : {}),
      ...(options.android.ttlMs ? { ttl: options.android.ttlMs } : {}),
      ...(includeNotification ? { notification: {
        ...(options.android.channelId ? { channelId: options.android.channelId } : {}),
        ...(options.android.notificationPriority ? { priority: options.android.notificationPriority } : {}),
        ...(options.android.tag ? { tag: options.android.tag } : {}),
      } } : {}),
    } : undefined;
    await messaging.getMessaging().send({
      token: fcmToken,
      ...(includeNotification ? { notification: { title, body } } : {}),
      data,
      android,
    });
    return true;
  }
}
export function getSender(): PushSender { return override ?? new FirebaseSender(); }
