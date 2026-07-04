export interface PushSender {
  send(fcmToken: string, title: string, body: string, data?: Record<string, string>): Promise<boolean>;
}

let override: PushSender | null = null;
export function setSender(s: PushSender) { override = s; }
export function resetSender() { override = null; }

class FirebaseSender implements PushSender {
  async send(fcmToken: string, title: string, body: string, data?: Record<string, string>) {
    if (!process.env.FCM_SERVICE_ACCOUNT_JSON) return false;
    const admin = await import('firebase-admin');
    if (!admin.apps.length) {
      admin.initializeApp({ credential: admin.credential.cert(JSON.parse(process.env.FCM_SERVICE_ACCOUNT_JSON)) });
    }
    await admin.messaging().send({ token: fcmToken, notification: { title, body }, data });
    return true;
  }
}
export function getSender(): PushSender { return override ?? new FirebaseSender(); }
