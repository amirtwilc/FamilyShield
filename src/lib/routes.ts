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
export type FrequentRoute = {
  from: LatLng;
  to: LatLng;
  count: number;
  lastAt: string;
  avgMinutes: number;
  avgKm: number;
  occurrenceKeys: string[];
  points: GpsPoint[];
};
export type FrequentLocation = {
  lat: number;
  lng: number;
  count: number;
  lastAt: string;
  occurrenceKeys: string[];
};

/** Tunable route-analysis defaults. ROUTE_STOP_RADIUS_M is the maximum jitter
 *  around a resting point; ROUTE_CONTINUATION_PAUSE_MIN is the dwell time needed
 *  to split movement into two routes. */
export const ROUTE_STOP_RADIUS_M = 150;
export const ROUTE_CONTINUATION_PAUSE_MIN = 5;
/** Maximum GPS variation for two trip endpoints to represent the same place.
 * Routes are otherwise matched strictly by their canonical start/end places,
 * so an intermediate stop cannot make a partial trip part of a longer route. */
export const FREQUENT_ROUTE_ENDPOINT_RADIUS_M = 150;
export const FREQUENT_ROUTE_MIN_COUNT = 2;
export const FREQUENT_ROUTE_LIMIT = 5;
/** Frequent locations are recurring detected stops, never route or movement
 * points. The radius absorbs ordinary GPS drift around the same place. */
export const FREQUENT_LOCATION_RADIUS_M = 150;
export const FREQUENT_LOCATION_MIN_COUNT = 2;
export const FREQUENT_LOCATION_LIMIT = 5;
/** A short-lived GPS cluster can be noise even when it is farther than the stop
 *  radius. If stable clusters before and after it are within this distance,
 *  route detection treats the noisy middle cluster as part of the same stop. */
export const ROUTE_OUTLIER_BRIDGE_RADIUS_M = 300;

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
  const sorted = points.filter(isValidGpsPoint).sort((a, b) => +new Date(a.at) - +new Date(b.at));
  const grouped = bridgeIsolatedOutliers(groupPoints(sorted, radiusM), radiusM, minDwellMin);
  const stops: Stop[] = [];

  for (const group of grouped) {
    const dwell = minutesBetween(group[0].at, group[group.length - 1].at);
    if (dwell >= minDwellMin) {
      const lat = group.reduce((s, p) => s + p.lat, 0) / group.length;
      const lng = group.reduce((s, p) => s + p.lng, 0) / group.length;
      stops.push({ lat, lng, arriveAt: group[0].at, departAt: group[group.length - 1].at, dwellMin: dwell });
    }
  }
  return stops;
}

function groupPoints(sorted: GpsPoint[], radiusM: number): GpsPoint[][] {
  const groups: GpsPoint[][] = [];
  let group: GpsPoint[] = [];

  const flush = () => {
    if (group.length === 0) return;
    groups.push(group);
    group = [];
  };

  for (const p of sorted) {
    if (group.length === 0) { group = [p]; continue; }
    const anchor = group[0];
    if (haversineM(anchor, p) <= radiusM) group.push(p);
    else { flush(); group = [p]; }
  }
  flush();
  return groups;
}

function bridgeIsolatedOutliers(groups: GpsPoint[][], radiusM: number, minDwellMin: number): GpsPoint[][] {
  const bridged: GpsPoint[][] = [];
  let i = 0;
  while (i < groups.length) {
    if (
      i + 2 < groups.length &&
      groupDwellMin(groups[i + 1]) < minDwellMin &&
      groups[i].length + groups[i + 2].length >= 3 &&
      haversineM(groupCenter(groups[i]), groupCenter(groups[i + 2])) <= Math.max(radiusM, ROUTE_OUTLIER_BRIDGE_RADIUS_M)
    ) {
      bridged.push([...groups[i], ...groups[i + 1], ...groups[i + 2]]);
      i += 3;
    } else {
      bridged.push(groups[i]);
      i += 1;
    }
  }
  return bridged.length === groups.length ? bridged : bridgeIsolatedOutliers(bridged, radiusM, minDwellMin);
}

function groupDwellMin(group: GpsPoint[]): number {
  return group.length < 2 ? 0 : minutesBetween(group[0].at, group[group.length - 1].at);
}

function groupCenter(group: GpsPoint[]): LatLng {
  return {
    lat: avg(group.map((p) => p.lat)),
    lng: avg(group.map((p) => p.lng)),
  };
}

function isValidGpsPoint(p: GpsPoint): boolean {
  return Number.isFinite(p.lat) && Number.isFinite(p.lng) &&
    p.lat >= -90 && p.lat <= 90 &&
    p.lng >= -180 && p.lng <= 180 &&
    !Number.isNaN(Date.parse(p.at));
}

/** Trips between consecutive stops (ignoring stays at the same place). */
export function buildTrips(stops: Stop[], points: GpsPoint[] = [], minTripM = 250): Trip[] {
  const trips: Trip[] = [];
  const sortedPoints = points.filter(isValidGpsPoint).sort((a, b) => +new Date(a.at) - +new Date(b.at));
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

type EndpointCluster = LatLng & { count: number };
type ClusteredTrip = { trip: Trip; fromCluster: number; toCluster: number };

export function tripOccurrenceKey(trip: Pick<Trip, 'departAt' | 'arriveAt'>): string {
  return `${trip.departAt}|${trip.arriveAt}`;
}

function clusterTripEndpoints(trips: Trip[], radiusM: number): ClusteredTrip[] {
  const clusters: EndpointCluster[] = [];

  const clusterFor = (point: LatLng): number => {
    let nearest = -1;
    let nearestDistance = Number.POSITIVE_INFINITY;
    clusters.forEach((cluster, index) => {
      const distance = haversineM(cluster, point);
      if (distance <= radiusM && distance < nearestDistance) {
        nearest = index;
        nearestDistance = distance;
      }
    });
    if (nearest < 0) {
      clusters.push({ ...point, count: 1 });
      return clusters.length - 1;
    }

    const cluster = clusters[nearest];
    const count = cluster.count + 1;
    cluster.lat = (cluster.lat * cluster.count + point.lat) / count;
    cluster.lng = (cluster.lng * cluster.count + point.lng) / count;
    cluster.count = count;
    return nearest;
  };

  return trips.map((trip) => ({
    trip,
    fromCluster: clusterFor(trip.from),
    toCluster: clusterFor(trip.to),
  }));
}

function directionKey(fromCluster: number, toCluster: number): string {
  return `${fromCluster}:${toCluster}`;
}

function undirectedKey(fromCluster: number, toCluster: number): string {
  return fromCluster < toCluster
    ? directionKey(fromCluster, toCluster)
    : directionKey(toCluster, fromCluster);
}

function orientClusteredTrip(entry: ClusteredTrip, fromCluster: number): Trip {
  return entry.fromCluster === fromCluster
    ? entry.trip
    : { ...entry.trip, from: entry.trip.to, to: entry.trip.from, points: [...entry.trip.points].reverse() };
}

/** Cluster trips by similar endpoints and surface the recurring routes. Recurrence
 *  is directional, but displayed routes are bidirectional: if A->B is recurring,
 *  B->A occurrences are included in the same A<->B summary row. */
export function frequentRoutes(
  trips: Trip[],
  endpointRadiusM = FREQUENT_ROUTE_ENDPOINT_RADIUS_M,
  minCount = FREQUENT_ROUTE_MIN_COUNT,
  limit = FREQUENT_ROUTE_LIMIT,
): FrequentRoute[] {
  const clustered = clusterTripEndpoints(trips, endpointRadiusM)
    .filter((entry) => entry.fromCluster !== entry.toCluster);
  const directed = new Map<string, ClusteredTrip[]>();
  const undirected = new Map<string, ClusteredTrip[]>();

  clustered.forEach((entry) => {
    const directedKey = directionKey(entry.fromCluster, entry.toCluster);
    directed.set(directedKey, [...(directed.get(directedKey) ?? []), entry]);
    const pairKey = undirectedKey(entry.fromCluster, entry.toCluster);
    undirected.set(pairKey, [...(undirected.get(pairKey) ?? []), entry]);
  });

  const summaries: FrequentRoute[] = [];
  for (const matchingEntries of undirected.values()) {
    const sample = matchingEntries[0];
    const forward = directed.get(directionKey(sample.fromCluster, sample.toCluster)) ?? [];
    const reverse = directed.get(directionKey(sample.toCluster, sample.fromCluster)) ?? [];
    if (forward.length < minCount && reverse.length < minCount) continue;

    const preferred = forward.length > reverse.length
      ? forward
      : reverse.length > forward.length
        ? reverse
        : [...forward, ...reverse].sort((a, b) =>
            +new Date(b.trip.arriveAt) - +new Date(a.trip.arriveAt),
          ).slice(0, 1);
    const fromCluster = preferred[0].fromCluster;
    const matching = matchingEntries.map((entry) => orientClusteredTrip(entry, fromCluster));
    const representative = [...matching].sort((a, b) =>
      +new Date(b.arriveAt) - +new Date(a.arriveAt),
    )[0];

    summaries.push({
      from: { lat: avg(matching.map((t) => t.from.lat)), lng: avg(matching.map((t) => t.from.lng)) },
      to: { lat: avg(matching.map((t) => t.to.lat)), lng: avg(matching.map((t) => t.to.lng)) },
      count: matching.length,
      lastAt: matching.map((t) => t.arriveAt).sort().at(-1)!,
      avgMinutes: avg(matching.map((t) => t.durationMin)),
      avgKm: avg(matching.map((t) => t.distanceKm)),
      occurrenceKeys: matching.map(tripOccurrenceKey),
      points: representative.points,
    });
  }
  return summaries
    .sort((a, b) => b.count - a.count || +new Date(b.lastAt) - +new Date(a.lastAt))
    .slice(0, limit);
}

export function stopOccurrenceKey(stop: Pick<Stop, 'arriveAt' | 'departAt'>): string {
  return `${stop.arriveAt}|${stop.departAt}`;
}

export function frequentLocations(
  stops: Stop[],
  radiusM = FREQUENT_LOCATION_RADIUS_M,
  minCount = FREQUENT_LOCATION_MIN_COUNT,
  limit = FREQUENT_LOCATION_LIMIT,
): FrequentLocation[] {
  const clusters: Array<{ lat: number; lng: number; stops: Stop[] }> = [];
  [...stops]
    .sort((a, b) => +new Date(a.arriveAt) - +new Date(b.arriveAt))
    .forEach((stop) => {
      const nearest = clusters
        .map((cluster, index) => ({ index, distance: haversineM(cluster, stop) }))
        .filter(({ distance }) => distance <= radiusM)
        .sort((a, b) => a.distance - b.distance)[0];
      if (!nearest) {
        clusters.push({ lat: stop.lat, lng: stop.lng, stops: [stop] });
        return;
      }
      const cluster = clusters[nearest.index];
      const count = cluster.stops.length + 1;
      cluster.lat = (cluster.lat * cluster.stops.length + stop.lat) / count;
      cluster.lng = (cluster.lng * cluster.stops.length + stop.lng) / count;
      cluster.stops.push(stop);
    });

  return clusters
    .filter((cluster) => cluster.stops.length >= minCount)
    .map((cluster) => ({
      lat: cluster.lat,
      lng: cluster.lng,
      count: cluster.stops.length,
      lastAt: cluster.stops.map((stop) => stop.departAt).sort().at(-1)!,
      occurrenceKeys: cluster.stops.map(stopOccurrenceKey),
    }))
    .sort((a, b) => b.count - a.count || +new Date(b.lastAt) - +new Date(a.lastAt))
    .slice(0, limit);
}

const avg = (xs: number[]) => xs.reduce((s, x) => s + x, 0) / xs.length;

export function analyzeRoutes(points: GpsPoint[]): {
  stops: Stop[];
  trips: Trip[];
  frequent: FrequentRoute[];
  frequentLocations: FrequentLocation[];
} {
  const stops = detectStops(points);
  const trips = buildTrips(stops, points);
  return { stops, trips, frequent: frequentRoutes(trips), frequentLocations: frequentLocations(stops) };
}
