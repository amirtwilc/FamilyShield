'use client';

import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';

// OpenStreetMap raster style — no Google Maps, per project constraints.
const OSM_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: 'raster',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      attribution: '© OpenStreetMap contributors',
    },
  },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
};

type Props = {
  center: [number, number]; // [lng, lat]
  marker?: [number, number] | null; // [lng, lat]
  draggable?: boolean;
  onMarkerMove?: (lng: number, lat: number) => void;
  testId?: string;
};

export default function MapView({ center, marker, draggable, onMarkerMove, testId }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markerRef = useRef<maplibregl.Marker | null>(null);
  const moveCb = useRef(onMarkerMove);
  moveCb.current = onMarkerMove;

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: OSM_STYLE,
      center,
      zoom: 13,
    });
    map.addControl(new maplibregl.NavigationControl(), 'top-right');
    mapRef.current = map;
    return () => { map.remove(); mapRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sync the marker position.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (!marker) {
      markerRef.current?.remove();
      markerRef.current = null;
      return;
    }
    if (!markerRef.current) {
      const m = new maplibregl.Marker({ color: '#1e6ef0', draggable: !!draggable }).setLngLat(marker).addTo(map);
      if (draggable) {
        m.on('dragend', () => { const p = m.getLngLat(); moveCb.current?.(p.lng, p.lat); });
      }
      markerRef.current = m;
    } else {
      markerRef.current.setLngLat(marker);
    }
    map.easeTo({ center: marker, duration: 400 });
  }, [marker, draggable]);

  return <div className="map" data-testid={testId ?? 'map'} ref={containerRef} />;
}
