import { describe, it, expect } from 'vitest';
import {
  detectStops,
  buildTrips,
  frequentLocations,
  frequentRoutes,
  analyzeRoutes,
  type GpsPoint,
  type Stop,
  type Trip,
} from '@/lib/routes';

const HOME = { lat: 32.000, lng: 34.000 };
const SCHOOL = { lat: 32.020, lng: 34.020 }; // ~2.8 km from HOME
const GYM = { lat: 50.934953, lng: 6.974577 };

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

  it('classifies a trip from its moving GPS speeds', () => {
    const points = twoDayHistory().map((point) => ({ ...point, speed: 11 }));
    const trips = buildTrips(detectStops(points), points);

    expect(trips.length).toBeGreaterThan(0);
    expect(trips.every((trip) => trip.movementMode === 'driving')).toBe(true);
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

  it('does not count one-off partial trips as occurrences of a longer route', () => {
    const midpoint = { lat: 32.010, lng: 34.010 };
    const directTrips = [
      trip(HOME, SCHOOL, '2026-06-20T08:00:00Z'),
      trip(HOME, SCHOOL, '2026-06-21T08:00:00Z'),
      trip(HOME, SCHOOL, '2026-06-22T08:00:00Z'),
    ];
    const partialTrips = [
      trip(HOME, midpoint, '2026-06-23T08:00:00Z'),
      trip(midpoint, SCHOOL, '2026-06-23T08:30:00Z'),
    ];

    const frequent = frequentRoutes([...directTrips, ...partialTrips]);

    expect(frequent).toHaveLength(1);
    expect(frequent[0].count).toBe(3);
    expect(frequent[0].occurrenceKeys).toEqual(
      directTrips.map((route) => `${route.departAt}|${route.arriveAt}`),
    );
    expect(frequent[0].points).toEqual(directTrips.at(-1)!.points);
  });

  it('surfaces only recurring stop locations and keeps exact occurrences', () => {
    const homeStops: Stop[] = [
      stop(HOME, '2026-06-20T06:00:00Z', '2026-06-20T08:00:00Z'),
      stop({ lat: 32.0002, lng: 34.0001 }, '2026-06-21T06:00:00Z', '2026-06-21T08:00:00Z'),
    ];
    const oneOffSchool = stop(SCHOOL, '2026-06-20T09:00:00Z', '2026-06-20T15:00:00Z');

    const frequent = frequentLocations([...homeStops, oneOffSchool]);

    expect(frequent).toHaveLength(1);
    expect(frequent[0].count).toBe(2);
    expect(frequent[0].occurrenceKeys).toEqual(
      homeStops.map((location) => `${location.arriveAt}|${location.departAt}`),
    );
  });

  it('limits frequent locations to five by count then recency', () => {
    const locations = Array.from({ length: 6 }, (_, index) => {
      const place = { lat: 31 + index * 0.02, lng: 34 };
      return [
        stop(place, `2026-06-${20 + index}T06:00:00Z`, `2026-06-${20 + index}T07:00:00Z`),
        stop(place, `2026-06-${20 + index}T08:00:00Z`, `2026-06-${20 + index}T09:00:00Z`),
      ];
    }).flat();

    const frequent = frequentLocations(locations);

    expect(frequent).toHaveLength(5);
    expect(frequent[0].lastAt).toBe('2026-06-25T09:00:00Z');
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

  it('keeps a stop continuous across isolated noisy or invalid GPS points', () => {
    const points: GpsPoint[] = [
      { ...HOME, at: '2026-07-27T17:00:00Z' },
      { ...HOME, at: '2026-07-27T17:10:00Z' },
      { ...GYM, at: '2026-07-27T17:24:00Z' },
      { lat: GYM.lat + 0.002, lng: GYM.lng, at: '2026-07-27T17:38:00Z' },
      { ...GYM, at: '2026-07-27T17:52:00Z' },
      { ...GYM, at: '2026-07-27T18:02:00Z' },
      { lat: 1.135e45, lng: GYM.lng, at: '2026-07-27T18:05:00Z' },
      { ...GYM, at: '2026-07-27T18:14:00Z' },
      { ...HOME, at: '2026-07-27T18:30:00Z' },
      { ...HOME, at: '2026-07-27T18:40:00Z' },
    ];

    const stops = detectStops(points);
    const gymStop = stops.find((stop) => Math.abs(stop.lat - GYM.lat) < 0.001);
    const trips = buildTrips(stops, points);

    expect(gymStop?.arriveAt).toBe('2026-07-27T17:24:00Z');
    expect(gymStop?.departAt).toBe('2026-07-27T18:14:00Z');
    expect(trips.flatMap((route) => route.points).some((point) => point.lat > 90)).toBe(false);
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
    movementMode: 'walking',
    points: [
      { ...from, at: departAt },
      { lat: (from.lat + to.lat) / 2, lng: (from.lng + to.lng) / 2, at: new Date(depart + 10 * 60_000).toISOString() },
      { ...to, at: arriveAt },
    ],
  };
}

function stop(place: typeof HOME, arriveAt: string, departAt: string): Stop {
  return {
    ...place,
    arriveAt,
    departAt,
    dwellMin: (Date.parse(departAt) - Date.parse(arriveAt)) / 60_000,
  };
}
