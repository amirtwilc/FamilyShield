// Browser-side typed wrappers over the FamilyShield API. Tokens live in
// localStorage; non-2xx responses throw with the API's error message.
'use client';

const PARENT_KEY = 'fs_parent_token';
const DEVICE_KEY = 'fs_device_token';

export const parentToken = {
  get: () => (typeof window === 'undefined' ? null : localStorage.getItem(PARENT_KEY)),
  set: (t: string) => localStorage.setItem(PARENT_KEY, t),
  clear: () => localStorage.removeItem(PARENT_KEY),
};
export const deviceToken = {
  get: () => (typeof window === 'undefined' ? null : localStorage.getItem(DEVICE_KEY)),
  set: (t: string) => localStorage.setItem(DEVICE_KEY, t),
  clear: () => localStorage.removeItem(DEVICE_KEY),
};

async function call<T>(path: string, opts: { method?: string; body?: unknown; token?: string | null } = {}): Promise<T> {
  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(opts.token ? { Authorization: `Bearer ${opts.token}` } : {}),
    },
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    if (res.status === 401) parentToken.clear();
    throw new Error(data?.error?.message ?? `Request failed (${res.status})`);
  }
  return data as T;
}

// ---- Parent ----
export type Tokens = { accessToken: string; refreshToken: string };
export type Device = {
  id: string; platform: string; model: string | null; batteryLevel: number | null;
  isCharging: boolean | null; lastSeenAt: string | null; revokedAt: string | null;
};
export type Child = { id: string; displayName: string; avatar: string; devices: Device[] };
export type CurrentLocation = { lat: number; lng: number; recordedAt: string } | null;
export type Alert = { id: string; type: string; payload: Record<string, unknown>; createdAt: string };
export type Monitor = { parentId: string; email: string; displayName: string; role: string };
export type MonitoringInfo = { childId: string; monitors: Monitor[] };
export type MonitorUnpairResult = MonitoringInfo & { unpaired: boolean };
export type Message = { id: string; sender: string; body: string; created_at: string; read_at: string | null };

export const registerParent = (email: string, password: string) =>
  call<Tokens>('/api/auth/register', { method: 'POST', body: { email, password } });
export const loginParent = (email: string, password: string) =>
  call<Tokens>('/api/auth/login', { method: 'POST', body: { email, password } });
export const listChildren = (token: string) =>
  call<{ children: Child[] }>('/api/children', { token }).then((r) => r.children);
export const createChild = (token: string, displayName: string, avatar?: string) =>
  call<Child>('/api/children', { method: 'POST', body: { displayName, ...(avatar ? { avatar } : {}) }, token });
export const updateChild = (token: string, childId: string, displayName: string, avatar?: string) =>
  call<Child>(`/api/children/${childId}`, { method: 'PATCH', body: { displayName, ...(avatar ? { avatar } : {}) }, token });
export const deleteChild = (token: string, childId: string) =>
  call<{ ok: boolean }>(`/api/children/${childId}`, { method: 'DELETE', token });
export const pairingCode = (token: string, childId: string) =>
  call<{ code: string; expiresAt: string }>(`/api/children/${childId}/pairing-code`, { method: 'POST', token });
export const currentLocation = (token: string, childId: string) =>
  call<CurrentLocation>(`/api/children/${childId}/location/current`, { token });
export const listAlerts = (token: string, childId: string) =>
  call<{ alerts: Alert[] }>(`/api/children/${childId}/alerts`, { token }).then((r) => r.alerts);

// ---- Kid device ----
export const pairDevice = (code: string, platform: string, model?: string) =>
  call<{ deviceToken: string; childId: string }>('/api/pair', { method: 'POST', body: { code, platform, model } });
export const addParentToDevice = (token: string, code: string, platform: string, model?: string) =>
  call<MonitoringInfo>('/api/pair', { method: 'POST', token, body: { code, platform, model } });
export const deviceMonitoring = (token: string) =>
  call<MonitoringInfo>('/api/device/monitoring', { token });
export const removeDeviceMonitor = (token: string, parentId: string) =>
  call<MonitorUnpairResult>(`/api/device/monitors/${parentId}`, { method: 'DELETE', token });
export const deviceMonitorMessages = (token: string, parentId: string) =>
  call<{ messages: Message[]; nextCursor: string | null }>(`/api/device/monitors/${parentId}/messages`, { token });
export const sendDeviceMonitorMessage = (token: string, parentId: string, body: string) =>
  call<Message>(`/api/device/monitors/${parentId}/messages`, { method: 'POST', token, body: { body } });
export const sendLocation = (token: string, p: { lat: number; lng: number; battery_level?: number }) =>
  call<{ inserted: number }>('/api/locations', {
    method: 'POST', token,
    body: { points: [{ ...p, recorded_at: new Date().toISOString() }] },
  });
export const sendStatus = (token: string, body: { battery_level: number; is_charging: boolean }) =>
  call<{ ok: true }>('/api/device/status', { method: 'POST', body, token });
