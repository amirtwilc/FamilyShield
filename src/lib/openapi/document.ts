import { OpenApiGeneratorV31 } from '@asteasolutions/zod-to-openapi';
import { registry } from './registry';
import { registerSchema, loginSchema, refreshSchema, googleSchema, tokenPairSchema } from '@/lib/schemas/auth';
import { pairSchema } from '@/lib/schemas/pair';
import { locationBatch } from '@/lib/schemas/locations';
import { statusSchema } from '@/lib/schemas/status';
import { deviceTelemetrySchema } from '@/lib/schemas/telemetry';
import { reportUsageSchema } from '@/lib/schemas/appusage';
import { createChildSchema } from '@/lib/schemas/children';
import { createZoneSchema } from '@/lib/schemas/zones';
import { sendMessageSchema } from '@/lib/schemas/messages';
import { pushTokenSchema, historyQuery } from '@/lib/schemas/parent';
import { z } from './registry';

let built: object | null = null;

export function buildOpenApiDocument() {
  if (built) return built;

  registry.registerComponent('securitySchemes', 'parentJwt', { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' });
  registry.registerComponent('securitySchemes', 'deviceToken', { type: 'http', scheme: 'bearer' });

  const json = (schema: unknown) => ({ content: { 'application/json': { schema: schema as any } } });

  registry.registerPath({ method: 'post', path: '/api/auth/register', request: { body: json(registerSchema) },
    responses: { 201: { description: 'Created', ...json(tokenPairSchema) }, 409: { description: 'Email taken' } } });
  registry.registerPath({ method: 'post', path: '/api/auth/login', request: { body: json(loginSchema) },
    responses: { 200: { description: 'OK', ...json(tokenPairSchema) }, 401: { description: 'Bad credentials' } } });
  registry.registerPath({ method: 'post', path: '/api/auth/refresh', request: { body: json(refreshSchema) },
    responses: { 200: { description: 'OK', ...json(tokenPairSchema) } } });
  registry.registerPath({ method: 'post', path: '/api/auth/google', request: { body: json(googleSchema) },
    responses: { 200: { description: 'OK', ...json(tokenPairSchema) }, 401: { description: 'Invalid Google token' } } });
  registry.registerPath({ method: 'get', path: '/api/health',
    responses: { 200: { description: 'Health status' } } });
  registry.registerPath({ method: 'post', path: '/api/pair', request: { body: json(pairSchema) },
    responses: { 201: { description: 'Paired' }, 400: { description: 'Invalid code' } } });
  registry.registerPath({ method: 'post', path: '/api/locations', security: [{ deviceToken: [] }],
    request: { body: json(locationBatch) }, responses: { 200: { description: 'Ingested' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'post', path: '/api/device/status', security: [{ deviceToken: [] }],
    request: { body: json(statusSchema) }, responses: { 200: { description: 'OK' } } });
  registry.registerPath({ method: 'post', path: '/api/device/telemetry', security: [{ deviceToken: [] }],
    request: { body: json(deviceTelemetrySchema) }, responses: { 200: { description: 'Telemetry ingested' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'post', path: '/api/device/app-usage', security: [{ deviceToken: [] }],
    request: { body: json(reportUsageSchema) }, responses: { 200: { description: 'App usage ingested' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'get', path: '/api/device/monitoring', security: [{ deviceToken: [] }],
    responses: { 200: { description: 'Current monitoring relationships' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'delete', path: '/api/device/monitors/{parentId}', security: [{ deviceToken: [] }],
    responses: { 200: { description: 'Monitor removed' }, 404: { description: 'Monitor not found' } } });
  registry.registerPath({ method: 'get', path: '/api/device/monitors/{parentId}/messages', security: [{ deviceToken: [] }],
    responses: { 200: { description: 'Messages with one monitor' }, 404: { description: 'Monitor not found' } } });
  registry.registerPath({ method: 'post', path: '/api/device/monitors/{parentId}/messages', security: [{ deviceToken: [] }],
    responses: { 201: { description: 'Message sent' }, 404: { description: 'Monitor not found' } } });
  registry.registerPath({ method: 'post', path: '/api/children', security: [{ parentJwt: [] }],
    request: { body: json(createChildSchema) }, responses: { 201: { description: 'Created' } } });
  registry.registerPath({ method: 'get', path: '/api/children', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'List' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Child detail' }, 404: { description: 'Child not found' } } });
  registry.registerPath({ method: 'post', path: '/api/children/{id}/pairing-code', security: [{ parentJwt: [] }],
    responses: { 201: { description: 'Code' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/location/current', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Current location' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/location/history', security: [{ parentJwt: [] }],
    request: { query: historyQuery }, responses: { 200: { description: 'History' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/alerts', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Alerts' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/routes', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Detected routes, trips, and stops' }, 404: { description: 'Child not found' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/messages', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Messages with a child' }, 404: { description: 'Child not found' } } });
  registry.registerPath({ method: 'post', path: '/api/children/{id}/messages', security: [{ parentJwt: [] }],
    request: { body: json(sendMessageSchema) }, responses: { 201: { description: 'Message sent' }, 404: { description: 'Child not found' } } });
  registry.registerPath({ method: 'get', path: '/api/messages/summary', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Conversation summary' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/app-usage', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'App usage summary', ...json(z.object({
      totalTodayMin: z.number(),
      yesterdayMin: z.number(),
      avgWeekMin: z.number(),
      week: z.array(z.object({ day: z.string(), dow: z.string(), min: z.number() })),
      apps: z.array(z.object({ packageName: z.string(), app: z.string(), category: z.string(), min: z.number() })),
      lastUpdatedAt: z.union([z.string(), z.null()]),
      appUsageAccessGranted: z.union([z.boolean(), z.null()]),
    })) } } });
  registry.registerPath({ method: 'post', path: '/api/children/{id}/zones', security: [{ parentJwt: [] }],
    request: { body: json(createZoneSchema) }, responses: { 201: { description: 'Zone' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/zones', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Safe zones' }, 404: { description: 'Child not found' } } });
  registry.registerPath({ method: 'delete', path: '/api/children/{id}/zones/{zoneId}', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Zone deleted' }, 404: { description: 'Child or zone not found' } } });
  registry.registerPath({ method: 'delete', path: '/api/devices/{id}', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Device revoked' }, 404: { description: 'Device not found' } } });
  registry.registerPath({ method: 'post', path: '/api/parent/push-token', security: [{ parentJwt: [] }],
    request: { body: json(pushTokenSchema) }, responses: { 200: { description: 'OK' } } });
  registry.registerPath({ method: 'get', path: '/api/cron/offline-sweep',
    responses: { 200: { description: 'Offline sweep complete' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'get', path: '/api/cron/location-retention',
    responses: { 200: { description: 'Location retention cleanup complete' }, 401: { description: 'Unauthorized' } } });

  const generator = new OpenApiGeneratorV31(registry.definitions);
  built = generator.generateDocument({
    openapi: '3.1.0',
    info: { title: 'FamilyShield API', version: '1.0.0' },
  });
  return built;
}
