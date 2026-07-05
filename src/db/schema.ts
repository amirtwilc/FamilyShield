import { sql } from 'drizzle-orm';
import {
  pgTable, uuid, text, integer, boolean, timestamp, real, jsonb,
  pgEnum, customType, uniqueIndex, index, date,
} from 'drizzle-orm/pg-core';

// PostGIS point as WKT in/out; queries use ST_* directly via raw sql.
export const point = customType<{ data: { lat: number; lng: number }; driverData: string }>({
  dataType() { return 'geometry(Point,4326)'; },
  toDriver(v) { return `SRID=4326;POINT(${v.lng} ${v.lat})`; },
});

export const alertType = pgEnum('alert_type', ['low_battery', 'offline', 'child_unpaired']);

export const subscriptionTiers = pgTable('subscription_tiers', {
  code: text('code').primaryKey(),
  name: text('name').notNull(),
  locationRetentionDays: integer('location_retention_days').notNull(),
  maxChildren: integer('max_children').notNull(),
  isActive: boolean('is_active').default(true).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).defaultNow().notNull(),
});

export const parents = pgTable('parents', {
  id: uuid('id').primaryKey().defaultRandom(),
  email: text('email').notNull().unique(),
  // Null for accounts created via Google sign-in (no local password).
  passwordHash: text('password_hash'),
  // Google account subject id, set when the parent signs in with Google.
  googleSub: text('google_sub'),
  fcmToken: text('fcm_token'),
  tierCode: text('tier_code').notNull().default('free')
    .references(() => subscriptionTiers.code, { onDelete: 'restrict' }),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
});

export const children = pgTable('children', {
  id: uuid('id').primaryKey().defaultRandom(),
  parentId: uuid('parent_id').notNull().references(() => parents.id, { onDelete: 'cascade' }),
  displayName: text('display_name').notNull(),
  avatar: text('avatar').notNull().default('fox'),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({ byParent: index('children_parent_idx').on(t.parentId) }));

export const childParentLinks = pgTable('child_parent_links', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  parentId: uuid('parent_id').notNull().references(() => parents.id, { onDelete: 'cascade' }),
  displayName: text('display_name').notNull(),
  role: text('role').notNull().default('caregiver'),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({
  byParent: index('child_parent_links_parent_idx').on(t.parentId),
  byChild: index('child_parent_links_child_idx').on(t.childId),
  uniqParentChild: uniqueIndex('child_parent_links_unique_idx').on(t.childId, t.parentId),
}));

export const devices = pgTable('devices', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  deviceTokenHash: text('device_token_hash').notNull().unique(),
  platform: text('platform').notNull(),
  model: text('model'),
  pairedAt: timestamp('paired_at', { withTimezone: true }).defaultNow().notNull(),
  revokedAt: timestamp('revoked_at', { withTimezone: true }),
  lastSeenAt: timestamp('last_seen_at', { withTimezone: true }),
  batteryLevel: integer('battery_level'),
  isCharging: boolean('is_charging'),
  fcmToken: text('fcm_token'),
  lastLocation: point('last_location'),
  lastLocationAt: timestamp('last_location_at', { withTimezone: true }),
  appUsageAccessGranted: boolean('app_usage_access_granted'),
  appUsageAccessCheckedAt: timestamp('app_usage_access_checked_at', { withTimezone: true }),
}, (t) => ({ byChild: index('devices_child_idx').on(t.childId) }));

export const pairingCodes = pgTable('pairing_codes', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  createdByParentId: uuid('created_by_parent_id').references(() => parents.id, { onDelete: 'cascade' }),
  code: text('code').notNull(),
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  consumedAt: timestamp('consumed_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  // Replaced in drizzle/0005 by a partial index on (code) WHERE consumed_at IS NULL.
}, (t) => ({ activeCode: index('pairing_codes_active_idx').on(t.code) }));

// NOTE: created as a partitioned table via raw SQL in Task 2's migration.
// Drizzle definition is for query typing only.
export const locations = pgTable('locations', {
  id: uuid('id').defaultRandom().notNull(),
  deviceId: uuid('device_id').notNull(),
  geom: point('geom').notNull(),
  speed: real('speed'),
  accuracy: real('accuracy'),
  batteryLevel: integer('battery_level'),
  recordedAt: timestamp('recorded_at', { withTimezone: true }).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({
  byDeviceTime: index('locations_device_time_idx').on(t.deviceId, t.recordedAt),
  dedupe: uniqueIndex('locations_dedupe_idx').on(t.deviceId, t.recordedAt),
}));

export const safeZones = pgTable('safe_zones', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  center: point('center').notNull(),
  radiusM: integer('radius_m').notNull(),
  notifyOnEnter: boolean('notify_on_enter').default(true).notNull(),
  notifyOnExit: boolean('notify_on_exit').default(true).notNull(),
  dwellMinutes: integer('dwell_minutes'),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
});

export const alerts = pgTable('alerts', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  deviceId: uuid('device_id'),
  type: alertType('type').notNull(),
  payload: jsonb('payload').default(sql`'{}'::jsonb`).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  deliveredAt: timestamp('delivered_at', { withTimezone: true }),
  readAt: timestamp('read_at', { withTimezone: true }),
}, (t) => ({ byChildTime: index('alerts_child_time_id_idx').on(t.childId, t.createdAt, t.id) }));

// Parent ⇄ kid direct messages (text chat). `sender` is 'parent' or 'child'.
export const messages = pgTable('messages', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  parentId: uuid('parent_id').references(() => parents.id, { onDelete: 'cascade' }),
  sender: text('sender').notNull(),
  body: text('body').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  readAt: timestamp('read_at', { withTimezone: true }),
  // Plus a partial index messages_unread_idx (child_id) WHERE read_at IS NULL,
  // created in drizzle/0005 for fast unread counts + mark-read.
}, (t) => ({
  byChildTime: index('messages_child_time_id_idx').on(t.childId, t.createdAt, t.id),
  byParentChildTime: index('messages_parent_child_time_id_idx').on(t.parentId, t.childId, t.createdAt, t.id),
}));

// Per-app screen-time minutes for a child on a given calendar day. One row per
// (child, app, day); a kid device reports/upserts these from UsageStatsManager.
export const appUsage = pgTable('app_usage', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  app: text('app').notNull(),
  category: text('category').notNull(),
  minutes: integer('minutes').notNull(),
  day: date('day').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({
  byChildDay: index('app_usage_child_day_idx').on(t.childId, t.day),
  uniqPerApp: uniqueIndex('app_usage_unique_idx').on(t.childId, t.app, t.day),
}));
