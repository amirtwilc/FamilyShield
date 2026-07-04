import { SignJWT, jwtVerify } from 'jose';
import { requireEnv } from '../env';

const enc = (s: string) => new TextEncoder().encode(s);
const accessKey = () => enc(requireEnv('JWT_SECRET'));
const refreshKey = () => enc(requireEnv('JWT_REFRESH_SECRET'));

async function sign(parentId: string, key: Uint8Array, ttl: string): Promise<string> {
  return new SignJWT({ sub: parentId })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt()
    .setExpirationTime(ttl)
    .sign(key);
}

export const signAccess = (parentId: string) => sign(parentId, accessKey(), '15m');
export const signRefresh = (parentId: string) => sign(parentId, refreshKey(), '30d');

async function verify(token: string, key: Uint8Array): Promise<string> {
  // Pin the algorithm so a token can't be presented under a different alg.
  const { payload } = await jwtVerify(token, key, { algorithms: ['HS256'] });
  if (typeof payload.sub !== 'string') throw new Error('no sub');
  return payload.sub;
}

export const verifyAccess = (t: string) => verify(t, accessKey());
export const verifyRefresh = (t: string) => verify(t, refreshKey());
