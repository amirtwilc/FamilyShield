'use client';

import { useEffect, useState, useCallback } from 'react';
import dynamic from 'next/dynamic';
import {
  parentToken, registerParent, loginParent, listChildren, createChild,
  pairingCode, currentLocation, listAlerts,
  type Child, type CurrentLocation, type Alert,
} from '@/lib/client/api';

// MapLibre touches window — load client-only.
const MapView = dynamic(() => import('@/components/client/MapView'), { ssr: false });

export default function ParentPage() {
  const [token, setToken] = useState<string | null>(null);
  useEffect(() => { setToken(parentToken.get()); }, []);

  if (!token) return <LoginForm onAuth={(t) => { parentToken.set(t); setToken(t); }} />;
  return <Dashboard onLogout={() => { parentToken.clear(); setToken(null); }} token={token} />;
}

function LoginForm({ onAuth }: { onAuth: (t: string) => void }) {
  const [mode, setMode] = useState<'register' | 'login'>('register');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      const fn = mode === 'register' ? registerParent : loginParent;
      const { accessToken } = await fn(email, password);
      onAuth(accessToken);
    } catch (err) { setError((err as Error).message); } finally { setBusy(false); }
  }

  return (
    <main className="shell" style={{ maxWidth: 420 }}>
      <h1>Parent {mode === 'register' ? 'sign up' : 'sign in'}</h1>
      <p className="muted">Access your family dashboard.</p>
      <form className="card" onSubmit={submit} data-testid="login-form">
        <label htmlFor="email">Email</label>
        <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <div className="row" style={{ marginTop: 16 }}>
          <button type="submit" disabled={busy} data-testid="login-submit">
            {busy ? '…' : mode === 'register' ? 'Create account' : 'Sign in'}
          </button>
          <button type="button" className="ghost"
            onClick={() => { setMode(mode === 'register' ? 'login' : 'register'); setError(''); }}>
            {mode === 'register' ? 'Have an account? Sign in' : 'New here? Sign up'}
          </button>
        </div>
        {error && <p className="error" data-testid="login-error">{error}</p>}
      </form>
    </main>
  );
}

function Dashboard({ token, onLogout }: { token: string; onLogout: () => void }) {
  const [children, setChildren] = useState<Child[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [newName, setNewName] = useState('');
  const [error, setError] = useState('');

  const refreshChildren = useCallback(async () => {
    try { setChildren(await listChildren(token)); }
    catch (err) { setError((err as Error).message); }
  }, [token]);

  useEffect(() => { refreshChildren(); }, [refreshChildren]);

  async function addChild(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    try {
      const c = await createChild(token, newName.trim());
      setNewName('');
      await refreshChildren();
      setSelected(c.id);
    } catch (err) { setError((err as Error).message); }
  }

  return (
    <main className="shell">
      <div className="spread">
        <h1>Family dashboard</h1>
        <button className="ghost" onClick={onLogout}>Log out</button>
      </div>
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <div className="card">
          <h2>Children</h2>
          <ul className="list" data-testid="child-list">
            {children.map((c) => (
              <li key={c.id} className={selected === c.id ? 'active' : ''}
                data-testid="child-item" onClick={() => setSelected(c.id)}>
                <div className="spread">
                  <strong>{c.displayName}</strong>
                  <span className="muted">{c.devices.length} device{c.devices.length === 1 ? '' : 's'}</span>
                </div>
              </li>
            ))}
            {children.length === 0 && <p className="muted">No children yet — add one below.</p>}
          </ul>
          <form className="row" onSubmit={addChild} style={{ marginTop: 8 }}>
            <input type="text" placeholder="Child name" value={newName}
              onChange={(e) => setNewName(e.target.value)} data-testid="child-name" />
            <button type="submit" data-testid="add-child">Add</button>
          </form>
          {error && <p className="error">{error}</p>}
        </div>

        {selected
          ? <ChildDetail token={token} child={children.find((c) => c.id === selected)!} onRefreshChildren={refreshChildren} />
          : <div className="card"><p className="muted">Select a child to see their location and alerts.</p></div>}
      </div>
    </main>
  );
}

function ChildDetail({ token, child, onRefreshChildren }: {
  token: string; child: Child; onRefreshChildren: () => Promise<void>;
}) {
  const [loc, setLoc] = useState<CurrentLocation>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [code, setCode] = useState<string | null>(null);
  const [error, setError] = useState('');
  const device = child.devices[0];

  const refresh = useCallback(async () => {
    setError('');
    try {
      // Also refresh the children list so device count, online status, and
      // battery (which live on the child's device record) update too.
      const [l, a] = await Promise.all([
        currentLocation(token, child.id),
        listAlerts(token, child.id),
        onRefreshChildren(),
      ]);
      setLoc(l); setAlerts(a);
    } catch (err) { setError((err as Error).message); }
  }, [token, child.id, onRefreshChildren]);

  useEffect(() => { setCode(null); refresh(); }, [refresh]);

  async function genCode() {
    try { setCode((await pairingCode(token, child.id)).code); }
    catch (err) { setError((err as Error).message); }
  }

  const online = device?.lastSeenAt
    && Date.now() - new Date(device.lastSeenAt).getTime() < 30 * 60_000;
  const batt = device?.batteryLevel;

  return (
    <div className="card" data-testid="child-detail">
      <div className="spread">
        <h2>{child.displayName}</h2>
        <button className="ghost" onClick={refresh} data-testid="refresh">Refresh</button>
      </div>

      <div className="row" style={{ marginBottom: 12 }}>
        <span className={`pill ${online ? 'online' : 'offline'}`} data-testid="status-pill">
          {online ? 'Online' : 'Offline'}
        </span>
        {batt != null && (
          <span className={`pill ${batt <= 15 ? 'batt-low' : 'offline'}`} data-testid="battery-pill">
            🔋 {batt}%{device?.isCharging ? ' ⚡' : ''}
          </span>
        )}
      </div>

      {loc ? (
        <>
          <MapView center={[loc.lng, loc.lat]} marker={[loc.lng, loc.lat]} testId="parent-map" />
          <p className="muted" data-testid="loc-text">
            {loc.lat.toFixed(5)}, {loc.lng.toFixed(5)} · {new Date(loc.recordedAt).toLocaleTimeString()}
          </p>
        </>
      ) : (
        <p className="muted" data-testid="no-location">No location yet. Pair a device and send one.</p>
      )}

      <h2 style={{ marginTop: 16 }}>Alerts</h2>
      <div data-testid="alerts">
        {alerts.length === 0 && <p className="muted">No alerts.</p>}
        {alerts.map((a) => (
          <div key={a.id} className={`alert ${a.type}`} data-testid={`alert-${a.type}`}>
            <span>{a.type === 'low_battery' ? '🔋' : a.type === 'offline' ? '📴' : '🔔'}</span>
            <span>{a.type.replace('_', ' ')} · {new Date(a.createdAt).toLocaleTimeString()}</span>
          </div>
        ))}
      </div>

      <h2 style={{ marginTop: 16 }}>Pair a device</h2>
      <div className="row">
        <button onClick={genCode} data-testid="gen-code">Generate pairing code</button>
        {code && <span className="code" data-testid="pairing-code">{code}</span>}
      </div>

      {error && <p className="error">{error}</p>}
    </div>
  );
}
