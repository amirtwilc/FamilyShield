import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { requireEnv } from '@/lib/env';

const PREFIX = 'enc:v1';

function key(): Buffer {
  const decoded = Buffer.from(requireEnv('MESSAGE_ENCRYPTION_KEY'), 'base64');
  if (decoded.length !== 32) {
    throw new Error('MESSAGE_ENCRYPTION_KEY must be a base64-encoded 32-byte key');
  }
  return decoded;
}

/** AES-256-GCM envelope stored in messages.body. */
export function encryptMessageBody(plaintext: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key(), iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  return [PREFIX, iv.toString('base64url'), ciphertext.toString('base64url'), cipher.getAuthTag().toString('base64url')].join(':');
}

/** Legacy plaintext rows remain readable until the history-encryption command migrates them. */
export function decryptMessageBody(stored: string): string {
  if (!stored.startsWith(`${PREFIX}:`)) return stored;
  const parts = stored.split(':');
  if (parts.length !== 5) throw new Error('Invalid encrypted message envelope');
  const iv = Buffer.from(parts[2]!, 'base64url');
  const ciphertext = Buffer.from(parts[3]!, 'base64url');
  const tag = Buffer.from(parts[4]!, 'base64url');
  const decipher = createDecipheriv('aes-256-gcm', key(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

export function isEncryptedMessageBody(stored: string): boolean {
  return stored.startsWith(`${PREFIX}:`);
}

export function decryptMessageRow<T extends { body: unknown }>(row: T): T {
  return { ...row, body: decryptMessageBody(String(row.body)) };
}
