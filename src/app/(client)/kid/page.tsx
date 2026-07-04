'use client';

import { useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import {
  addParentToDevice, deviceMonitoring, deviceToken, pairDevice, removeDeviceMonitor, sendLocation, sendStatus,
  type MonitoringInfo,
} from '@/lib/client/api';

const MapView = dynamic(() => import('@/components/client/MapView'), { ssr: false });

export default function KidPage() {
  const [token, setToken] = useState<string | null>(null);
  useEffect(() => { setToken(deviceToken.get()); }, []);

  if (!token) return <PairForm onPaired={(t) => { deviceToken.set(t); setToken(t); }} />;
  return <DeviceSim token={token} onUnpair={() => { deviceToken.clear(); setToken(null); }} />;
}

function PairForm({ onPaired }: { onPaired: (t: string) => void }) {
  const [code, setCode] = useState('');
  const [platform, setPlatform] = useState('android');
  const [ack, setAck] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      const { deviceToken: t } = await pairDevice(code.trim(), platform, 'Simulator');
      onPaired(t);
    } catch (err) { setError((err as Error).message); } finally { setBusy(false); }
  }

  return (
    <main className="shell" style={{ maxWidth: 420 }}>
      <h1>Pair this device</h1>
      <p className="muted">Enter the 6-digit code from the parent dashboard.</p>
      <form className="card" onSubmit={submit} data-testid="pair-form">
        <div className="alert">
          Pairing lets linked parents monitor this device's location, battery/status, app usage, and messages.
        </div>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} data-testid="pair-disclosure" />
          I understand this device will be monitored.
        </label>
        <label htmlFor="code">Pairing code</label>
        <input id="code" type="text" inputMode="numeric" maxLength={6} value={code}
          onChange={(e) => setCode(e.target.value)} data-testid="pair-code" required />
        <label htmlFor="platform">Platform</label>
        <select id="platform" value={platform} onChange={(e) => setPlatform(e.target.value)}>
          <option value="android">Android</option>
          <option value="ios">iOS</option>
        </select>
        <div className="row" style={{ marginTop: 16 }}>
          <button type="submit" disabled={busy || !ack} data-testid="pair-submit">{busy ? '…' : 'Pair'}</button>
        </div>
        {error && <p className="error" data-testid="pair-error">{error}</p>}
      </form>
    </main>
  );
}

function DeviceSim({ token, onUnpair }: { token: string; onUnpair: () => void }) {
  // Default near central Tel Aviv; the marker is draggable to "move" the device.
  const [lng, setLng] = useState(34.7818);
  const [lat, setLat] = useState(32.0853);
  const [battery, setBattery] = useState(80);
  const [charging, setCharging] = useState(false);
  const [msg, setMsg] = useState('');
  const [error, setError] = useState('');
  const [monitoring, setMonitoring] = useState<MonitoringInfo | null>(null);
  const [addingParent, setAddingParent] = useState(false);
  const [addCode, setAddCode] = useState('');
  const [addAck, setAddAck] = useState(false);

  async function refreshMonitoring() {
    try { setMonitoring(await deviceMonitoring(token)); }
    catch (err) { setError((err as Error).message); }
  }

  useEffect(() => { refreshMonitoring(); }, [token]);

  async function doSendLocation() {
    setError(''); setMsg('');
    try {
      await sendLocation(token, { lat, lng, battery_level: battery });
      setMsg('Location sent.');
    } catch (err) { setError((err as Error).message); }
  }
  async function doSendStatus() {
    setError(''); setMsg('');
    try {
      await sendStatus(token, { battery_level: battery, is_charging: charging });
      setMsg('Status sent.');
    } catch (err) { setError((err as Error).message); }
  }
  async function doAddParent(e: React.FormEvent) {
    e.preventDefault();
    setError(''); setMsg('');
    try {
      setMonitoring(await addParentToDevice(token, addCode.trim(), 'android', 'Simulator'));
      setAddCode(''); setAddAck(false); setAddingParent(false); setMsg('Parent added.');
    } catch (err) { setError((err as Error).message); }
  }

  return (
    <main className="shell">
      <div className="spread">
        <h1>📱 Device simulator</h1>
      </div>
      <p className="muted">Paired. Drag the marker to move, set battery, and send updates.</p>

      <div className="card" style={{ marginTop: 16 }}>
        <div className="spread">
          <h2>Monitored by</h2>
          <button className="ghost" onClick={() => setAddingParent((v) => !v)} data-testid="add-parent-open" aria-label="Pair another parent">
            +
          </button>
        </div>
        {monitoring?.monitors.length
          ? <ul className="list" data-testid="monitor-list">
              {monitoring.monitors.map((m) => (
                <li key={m.parentId}>
                  <span>{m.email}</span>
                  <span className="row">
                    <button className="ghost" onClick={() => alert('Use the Android app for parent-specific kid chat.')}>Message</button>
                    <button className="ghost" onClick={async () => {
                      if (!confirm('Are you sure?')) return;
                      const result = await removeDeviceMonitor(token, m.parentId);
                      if (result.unpaired) onUnpair();
                      else setMonitoring(result);
                    }}>Unpair</button>
                  </span>
                </li>
              ))}
            </ul>
          : <p className="muted">Loading monitors...</p>}
        {addingParent && (
          <form onSubmit={doAddParent} data-testid="add-parent-form">
            <div className="alert">
              Adding this code lets another parent monitor this device's location, battery/status, app usage, and messages.
            </div>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input type="checkbox" checked={addAck} onChange={(e) => setAddAck(e.target.checked)} data-testid="add-parent-disclosure" />
              I understand another parent will be able to monitor this device.
            </label>
            <label htmlFor="add-code">Pairing code</label>
            <input id="add-code" type="text" inputMode="numeric" maxLength={6} value={addCode}
              onChange={(e) => setAddCode(e.target.value)} data-testid="add-parent-code" required />
            <button type="submit" disabled={!addAck || addCode.length !== 6} data-testid="add-parent-submit">Add parent</button>
          </form>
        )}
      </div>

      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <div className="card">
          <h2>Telemetry</h2>
          <label>Latitude</label>
          <input type="text" value={lat.toFixed(5)} readOnly data-testid="sim-lat" />
          <label>Longitude</label>
          <input type="text" value={lng.toFixed(5)} readOnly data-testid="sim-lng" />
          <label htmlFor="batt">Battery: {battery}%</label>
          <input id="batt" type="range" min={0} max={100} value={battery}
            onChange={(e) => setBattery(Number(e.target.value))} data-testid="sim-battery" />
          <div className="row" style={{ marginTop: 6 }}>
            <label style={{ margin: 0 }}>
              <input type="checkbox" checked={charging}
                onChange={(e) => setCharging(e.target.checked)} data-testid="sim-charging" /> Charging
            </label>
          </div>
          <div className="row" style={{ marginTop: 16 }}>
            <button onClick={doSendLocation} data-testid="send-location">Send location</button>
            <button className="ghost" onClick={doSendStatus} data-testid="send-status">Send status</button>
          </div>
          {msg && <p className="ok-msg" data-testid="sim-msg">{msg}</p>}
          {error && <p className="error" data-testid="sim-error">{error}</p>}
        </div>

        <div className="card">
          <h2>Position</h2>
          <MapView center={[lng, lat]} marker={[lng, lat]} draggable testId="kid-map"
            onMarkerMove={(newLng, newLat) => { setLng(newLng); setLat(newLat); }} />
          <p className="muted">Tip: drag the blue marker to change the reported location.</p>
        </div>
      </div>
    </main>
  );
}
