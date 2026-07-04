export function encodeCursor(v: { recordedAt: string; id: string }): string {
  return Buffer.from(JSON.stringify(v), 'utf8').toString('base64url');
}
export function decodeCursor(s: string): { recordedAt: string; id: string } | null {
  try {
    const v = JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));
    if (typeof v?.recordedAt === 'string' && typeof v?.id === 'string') return v;
    return null;
  } catch { return null; }
}
