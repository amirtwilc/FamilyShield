import { describe, it, expect } from 'vitest';
import { detectStops, buildTrips, frequentRoutes, analyzeRoutes, type GpsPoint, type Trip } from '@/lib/routes';

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
    const trips = buildTrips(detectStops(twoDayHistory()), twoDayHistory());
    expect(trips.length).toBeGreaterThanOrEqual(2);
    // Each trip moved a meaningful distance.
    expect(trips.every((t) => t.distanceKm > 0.25)).toBe(true);
    expect(trips.some((t) => t.points.length > 2)).toBe(true);
  });

  it('surfaces the recurring home<->school route', () => {
    const { frequent } = analyzeRoutes(twoDayHistory());
    expect(frequent.length).toBeGreaterThan(0);
    const top = frequent[0];
    expect(top.count).toBeGreaterThanOrEqual(2);  // happened on both days
    expect(top.avgKm).toBeGreaterThan(0.25);
  });

  it('requires at least two occurrences to be "frequent"', () => {
    const trips = buildTrips(detectStops(twoDayHistory()), twoDayHistory());
    expect(frequentRoutes(trips, 300, 99)).toHaveLength(0);
  });

  it('displays one bidirectional route when only one direction is frequent', () => {
    const trips = [
      trip(SCHOOL, HOME, '2026-06-20T08:00:00Z'),
      trip(SCHOOL, HOME, '2026-06-21T08:00:00Z'),
    ];

    const frequent = frequentRoutes(trips);

    expect(frequent).toHaveLength(1);
    expect(frequent[0].count).toBe(2);
    expect(frequent[0].from.lat).toBeCloseTo(SCHOOL.lat);
    expect(frequent[0].to.lat).toBeCloseTo(HOME.lat);
  });

  it('does not make two opposite one-off trips frequent', () => {
    const trips = [
      trip(HOME, SCHOOL, '2026-06-20T08:00:00Z'),
      trip(SCHOOL, HOME, '2026-06-20T15:00:00Z'),
    ];

    expect(frequentRoutes(trips)).toHaveLength(0);
  });

  it('limits frequent route summaries to the top five by count then recency', () => {
    const routes = Array.from({ length: 6 }, (_, index) => {
      const from = { lat: 32 + index * 0.02, lng: 34 };
      const to = { lat: 32 + index * 0.02, lng: 34.03 };
      return [
        trip(from, to, `2026-06-${20 + index}T08:00:00Z`),
        trip(from, to, `2026-06-${20 + index}T09:00:00Z`),
      ];
    }).flat();

    const frequent = frequentRoutes(routes);

    expect(frequent).toHaveLength(5);
    expect(Date.parse(frequent[0].lastAt)).toBe(Date.parse('2026-06-25T09:20:00Z'));
  });
});

function trip(from: typeof HOME, to: typeof HOME, departAt: string): Trip {
  const depart = Date.parse(departAt);
  const arriveAt = new Date(depart + 20 * 60_000).toISOString();
  return {
    from,
    to,
    departAt,
    arriveAt,
    durationMin: 20,
    distanceKm: 1,
    points: [
      { ...from, at: departAt },
      { lat: (from.lat + to.lat) / 2, lng: (from.lng + to.lng) / 2, at: new Date(depart + 10 * 60_000).toISOString() },
      { ...to, at: arriveAt },
    ],
  };
}
