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

class FirebaseSender implements PushSender {
  async send(fcmToken: string, title: string, body: string, data?: Record<string, string>, options?: PushOptions) {
    if (!process.env.FIREBASE_SERVICE_ACCOUNT_JSON
      && !process.env.FCM_SERVICE_ACCOUNT_JSON
      && !process.env.GOOGLE_APPLICATION_CREDENTIALS) {
      console.warn('[push] Firebase Admin credentials are not set; skipping push send');
      return false;
    }
    const [{ getFirebaseAdminApp }, messaging] = await Promise.all([
      import('@/lib/firebase/admin'),
      import('firebase-admin/messaging'),
    ]);
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
    await messaging.getMessaging(getFirebaseAdminApp()).send({
      token: fcmToken,
      ...(includeNotification ? { notification: { title, body } } : {}),
      data,
      android,
    });
    return true;
  }
}
export function getSender(): PushSender { return override ?? new FirebaseSender(); }
