// Route detection from raw GPS history. Given a child's location points over a
// window, it finds "stops" (places where they stayed), the "trips" between them
// (departure → arrival), and the routes that recur frequently (e.g. home→school).
// Pure functions — unit-tested in test/lib/routes.test.ts.

export type GpsPoint = { lat: number; lng: number; at: string }; // at = ISO timestamp
export type LatLng = { lat: number; lng: number };
export type Stop = { lat: number; lng: number; arriveAt: string; departAt: string; dwellMin: number };
export type Trip = { from: LatLng; to: LatLng; departAt: string; arriveAt: string; durationMin: number; distanceKm: number };
export type FrequentRoute = { from: LatLng; to: LatLng; count: number; lastAt: string; avgMinutes: number; avgKm: number };

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
export function detectStops(points: GpsPoint[], radiusM = 150, minDwellMin = 5): Stop[] {
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
export function buildTrips(stops: Stop[], minTripM = 250): Trip[] {
  const trips: Trip[] = [];
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
    });
  }
  return trips;
}

/** Cluster trips by similar endpoints and surface the ones that recur. */
export function frequentRoutes(trips: Trip[], proximityM = 300, minCount = 2): FrequentRoute[] {
  const clusters: Trip[][] = [];
  for (const t of trips) {
    const c = clusters.find((cl) =>
      haversineM(cl[0].from, t.from) <= proximityM && haversineM(cl[0].to, t.to) <= proximityM);
    if (c) c.push(t); else clusters.push([t]);
  }
  return clusters
    .filter((cl) => cl.length >= minCount)
    .map((cl) => ({
      from: { lat: avg(cl.map((t) => t.from.lat)), lng: avg(cl.map((t) => t.from.lng)) },
      to: { lat: avg(cl.map((t) => t.to.lat)), lng: avg(cl.map((t) => t.to.lng)) },
      count: cl.length,
      lastAt: cl.map((t) => t.arriveAt).sort().at(-1)!,
      avgMinutes: avg(cl.map((t) => t.durationMin)),
      avgKm: avg(cl.map((t) => t.distanceKm)),
    }))
    .sort((a, b) => b.count - a.count);
}

const avg = (xs: number[]) => xs.reduce((s, x) => s + x, 0) / xs.length;

export function analyzeRoutes(points: GpsPoint[]): { stops: Stop[]; trips: Trip[]; frequent: FrequentRoute[] } {
  const stops = detectStops(points);
  const trips = buildTrips(stops);
  return { stops, trips, frequent: frequentRoutes(trips) };
}
