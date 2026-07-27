// Route detection from raw GPS history. Given a child's location points over a
// window, it finds "stops" (places where they stayed), the "trips" between them
// (departure → arrival), and the routes that recur frequently (e.g. home→school).
// Pure functions — unit-tested in test/lib/routes.test.ts.

export type GpsPoint = { lat: number; lng: number; at: string }; // at = ISO timestamp
export type LatLng = { lat: number; lng: number };
export type Stop = { lat: number; lng: number; arriveAt: string; departAt: string; dwellMin: number };
export type Trip = {
  from: LatLng;
  to: LatLng;
  departAt: string;
  arriveAt: string;
  durationMin: number;
  distanceKm: number;
  points: GpsPoint[];
};
export type FrequentRoute = { from: LatLng; to: LatLng; count: number; lastAt: string; avgMinutes: number; avgKm: number };

/** Tunable route-analysis defaults. ROUTE_STOP_RADIUS_M is the maximum jitter
 *  around a resting point; ROUTE_CONTINUATION_PAUSE_MIN is the dwell time needed
 *  to split movement into two routes. */
export const ROUTE_STOP_RADIUS_M = 150;
export const ROUTE_CONTINUATION_PAUSE_MIN = 5;
export const FREQUENT_ROUTE_PROXIMITY_M = 300;
export const FREQUENT_ROUTE_MIN_COUNT = 2;
export const FREQUENT_ROUTE_LIMIT = 5;

export function haversineM(a: LatLng, b: LatLng): number {
  const R = 6_371_000;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLng = ((b.lng - a.lng) * Math.PI) / 180;
  const la1 = (a.lat * Math.PI) / 180;
  const la2 = (b.lat * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

const minutesBetween = (a: string, b: string) => (new Date(b).getTime() - new Date(a).getTime()) / 60000;

/** Group consecutive points that stay within `radiusM`; a group whose time span
 *  is at least `minDwellMin` becomes a Stop (a place the child stayed). */
export function detectStops(
  points: GpsPoint[],
  radiusM = ROUTE_STOP_RADIUS_M,
  minDwellMin = ROUTE_CONTINUATION_PAUSE_MIN,
): Stop[] {
  const sorted = [...points].sort((a, b) => +new Date(a.at) - +new Date(b.at));
  const stops: Stop[] = [];
  let group: GpsPoint[] = [];

  const flush = () => {
    if (group.length === 0) return;
    const dwell = minutesBetween(group[0].at, group[group.length - 1].at);
    if (dwell >= minDwellMin) {
      const lat = group.reduce((s, p) => s + p.lat, 0) / group.length;
      const lng = group.reduce((s, p) => s + p.lng, 0) / group.length;
      stops.push({ lat, lng, arriveAt: group[0].at, departAt: group[group.length - 1].at, dwellMin: dwell });
    }
    group = [];
  };

  for (const p of sorted) {
    if (group.length === 0) { group = [p]; continue; }
    const anchor = group[0];
    if (haversineM(anchor, p) <= radiusM) group.push(p);
    else { flush(); group = [p]; }
  }
  flush();
  return stops;
}

/** Trips between consecutive stops (ignoring stays at the same place). */
export function buildTrips(stops: Stop[], points: GpsPoint[] = [], minTripM = 250): Trip[] {
  const trips: Trip[] = [];
  const sortedPoints = [...points].sort((a, b) => +new Date(a.at) - +new Date(b.at));
  for (let i = 1; i < stops.length; i++) {
    const a = stops[i - 1];
    const b = stops[i];
    const d = haversineM(a, b);
    if (d < minTripM) continue; // same place / negligible movement
    trips.push({
      from: { lat: a.lat, lng: a.lng },
      to: { lat: b.lat, lng: b.lng },
      departAt: a.departAt,
      arriveAt: b.arriveAt,
      durationMin: Math.max(0, minutesBetween(a.departAt, b.arriveAt)),
      distanceKm: d / 1000,
      points: routePoints(sortedPoints, a, b),
    });
  }
  return trips;
}

function routePoints(points: GpsPoint[], from: Stop, to: Stop): GpsPoint[] {
  const start = +new Date(from.departAt);
  const end = +new Date(to.arriveAt);
  const during = points.filter((p) => {
    const at = +new Date(p.at);
    return at >= start && at <= end;
  });
  return [
    { lat: from.lat, lng: from.lng, at: from.departAt },
    ...during,
    { lat: to.lat, lng: to.lng, at: to.arriveAt },
  ];
}

function sameDirection(a: Trip, b: Trip, proximityM: number): boolean {
  return haversineM(a.from, b.from) <= proximityM && haversineM(a.to, b.to) <= proximityM;
}

function eitherDirection(a: Trip, b: Trip, proximityM: number): boolean {
  return sameDirection(a, b, proximityM) ||
    (haversineM(a.from, b.to) <= proximityM && haversineM(a.to, b.from) <= proximityM);
}

function orientTrip(t: Trip, anchor: Trip, proximityM: number): Trip {
  if (sameDirection(t, anchor, proximityM)) return t;
  return { ...t, from: t.to, to: t.from };
}

/** Cluster trips by similar endpoints and surface the recurring routes. Recurrence
 *  is directional, but displayed routes are bidirectional: if A->B is recurring,
 *  B->A occurrences are included in the same A<->B summary row. */
export function frequentRoutes(
  trips: Trip[],
  proximityM = FREQUENT_ROUTE_PROXIMITY_M,
  minCount = FREQUENT_ROUTE_MIN_COUNT,
  limit = FREQUENT_ROUTE_LIMIT,
): FrequentRoute[] {
  const clusters: Trip[][] = [];
  for (const t of trips) {
    const c = clusters.find((cl) => sameDirection(cl[0], t, proximityM));
    if (c) c.push(t); else clusters.push([t]);
  }
  const frequentClusters = clusters.filter((cl) => cl.length >= minCount);
  const displayAnchors: Trip[] = [];
  const summaries: FrequentRoute[] = [];

  for (const frequent of frequentClusters) {
    const anchor = frequent[0];
    if (displayAnchors.some((existing) => eitherDirection(existing, anchor, proximityM))) continue;
    displayAnchors.push(anchor);
    const matching = trips.filter((t) => eitherDirection(anchor, t, proximityM)).map((t) => orientTrip(t, anchor, proximityM));
    summaries.push({
      from: { lat: avg(matching.map((t) => t.from.lat)), lng: avg(matching.map((t) => t.from.lng)) },
      to: { lat: avg(matching.map((t) => t.to.lat)), lng: avg(matching.map((t) => t.to.lng)) },
      count: matching.length,
      lastAt: matching.map((t) => t.arriveAt).sort().at(-1)!,
      avgMinutes: avg(matching.map((t) => t.durationMin)),
      avgKm: avg(matching.map((t) => t.distanceKm)),
    });
  }
  return summaries
    .sort((a, b) => b.count - a.count || +new Date(b.lastAt) - +new Date(a.lastAt))
    .slice(0, limit);
}

const avg = (xs: number[]) => xs.reduce((s, x) => s + x, 0) / xs.length;

export function analyzeRoutes(points: GpsPoint[]): { stops: Stop[]; trips: Trip[]; frequent: FrequentRoute[] } {
  const stops = detectStops(points);
  const trips = buildTrips(stops, points);
  return { stops, trips, frequent: frequentRoutes(trips) };
}
