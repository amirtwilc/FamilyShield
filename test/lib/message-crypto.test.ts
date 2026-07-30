import { describe, expect, it } from 'vitest';
import { decryptMessageBody, encryptMessageBody, isEncryptedMessageBody } from '@/lib/messages/crypto';

describe('message encryption', () => {
  it('round-trips authenticated ciphertext without retaining plaintext', () => {
    const plaintext = 'Meet me at 17:30 🔒';
    const stored = encryptMessageBody(plaintext);
    expect(isEncryptedMessageBody(stored)).toBe(true);
    expect(stored).not.toContain(plaintext);
    expect(decryptMessageBody(stored)).toBe(plaintext);
  });

  it('reads legacy plaintext during migration', () => {
    expect(decryptMessageBody('legacy row')).toBe('legacy row');
  });

  it('rejects tampered ciphertext', () => {
    const parts = encryptMessageBody('private').split(':');
    parts[3] = `${parts[3]![0] === 'A' ? 'B' : 'A'}${parts[3]!.slice(1)}`;
    expect(() => decryptMessageBody(parts.join(':'))).toThrow();
  });
});
