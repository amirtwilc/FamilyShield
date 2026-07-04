import { describe, it, expect } from 'vitest';
import { detectStops, buildTrips, frequentRoutes, analyzeRoutes, type GpsPoint } from '@/lib/routes';

const HOME = { lat: 32.000, lng: 34.000 };
const SCHOOL = { lat: 32.020, lng: 34.020 }; // ~2.8 km from HOME

// Build a synthetic two-day history: stay home → go to school → stay → go home.
function twoDayHistory(): GpsPoint[] {
  const base = Date.parse('2026-06-20T06:00:00Z');
  let t = 0;
  const pts: GpsPoint[] = [];
  const at = () => new Date(base + t * 60_000).toISOString();
  const stay = (p: { lat: number; lng: number }, mins: number) => {
    for (let m = 0; m < mins; m += 2) { pts.push({ lat: p.lat, lng: p.lng, at: at() }); t += 2; }
  };
  const move = (a: typeof HOME, b: typeof HOME, mins: number) => {
    const steps = 5;
    for (let i = 1; i <= steps; i++) {
      const f = i / steps;
      pts.push({ lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f, at: at() });
      t += mins / steps;
    }
  };
  const day = () => { stay(HOME, 12); move(HOME, SCHOOL, 20); stay(SCHOOL, 12); move(SCHOOL, HOME, 20); };
  day();           // day 1
  stay(HOME, 12);  // overnight at home
  t += 8 * 60;     // next morning
  day();           // day 2
  stay(HOME, 12);
  return pts;
}

describe('route detection', () => {
  it('detects home and school as stops', () => {
    const stops = detectStops(twoDayHistory());
    // At least one near HOME and one near SCHOOL.
    expect(stops.some((s) => Math.abs(s.lat - HOME.lat) < 0.002)).toBe(true);
    expect(stops.some((s) => Math.abs(s.lat - SCHOOL.lat) < 0.002)).toBe(true);
  });

  it('builds trips and ignores staying in place', () => {
    const trips = buildTrips(detectStops(twoDayHistory()));
    expect(trips.length).toBeGreaterThanOrEqual(2);
    // Each trip moved a meaningful distance.
    expect(trips.every((t) => t.distanceKm > 0.25)).toBe(true);
  });

  it('surfaces the recurring home<->school route', () => {
    const { frequent } = analyzeRoutes(twoDayHistory());
    expect(frequent.length).toBeGreaterThan(0);
    const top = frequent[0];
    expect(top.count).toBeGreaterThanOrEqual(2);  // happened on both days
    expect(top.avgKm).toBeGreaterThan(0.25);
  });

  it('requires at least two occurrences to be "frequent"', () => {
    const trips = buildTrips(detectStops(twoDayHistory()));
    expect(frequentRoutes(trips, 300, 99)).toHaveLength(0);
  });
});
