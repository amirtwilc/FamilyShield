CREATE EXTENSION IF NOT EXISTS postgis;

-- locations is RANGE-partitioned by recorded_at (monthly). PK must include the
-- partition key, so PK is (id, recorded_at).
DROP TABLE IF EXISTS locations CASCADE;
CREATE TABLE locations (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  geom geometry(Point,4326) NOT NULL,
  speed real,
  accuracy real,
  battery_level integer,
  recorded_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE UNIQUE INDEX locations_dedupe_idx ON locations (device_id, recorded_at);
CREATE INDEX locations_device_time_idx ON locations (device_id, recorded_at DESC);
CREATE INDEX locations_geom_idx ON locations USING GIST (geom);
