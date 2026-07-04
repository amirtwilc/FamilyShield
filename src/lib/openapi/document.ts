import { OpenApiGeneratorV31 } from '@asteasolutions/zod-to-openapi';
import { registry } from './registry';
import { registerSchema, loginSchema, refreshSchema, tokenPairSchema } from '@/lib/schemas/auth';
import { pairSchema } from '@/lib/schemas/pair';
import { locationBatch } from '@/lib/schemas/locations';
import { statusSchema } from '@/lib/schemas/status';
import { createChildSchema } from '@/lib/schemas/children';
import { createZoneSchema } from '@/lib/schemas/zones';
import { pushTokenSchema, historyQuery } from '@/lib/schemas/parent';

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
  registry.registerPath({ method: 'post', path: '/api/pair', request: { body: json(pairSchema) },
    responses: { 201: { description: 'Paired' }, 400: { description: 'Invalid code' } } });
  registry.registerPath({ method: 'post', path: '/api/locations', security: [{ deviceToken: [] }],
    request: { body: json(locationBatch) }, responses: { 200: { description: 'Ingested' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'post', path: '/api/device/status', security: [{ deviceToken: [] }],
    request: { body: json(statusSchema) }, responses: { 200: { description: 'OK' } } });
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
  registry.registerPath({ method: 'post', path: '/api/children/{id}/pairing-code', security: [{ parentJwt: [] }],
    responses: { 201: { description: 'Code' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/location/current', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Current location' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/location/history', security: [{ parentJwt: [] }],
    request: { query: historyQuery }, responses: { 200: { description: 'History' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/alerts', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Alerts' } } });
  registry.registerPath({ method: 'post', path: '/api/children/{id}/zones', security: [{ parentJwt: [] }],
    request: { body: json(createZoneSchema) }, responses: { 201: { description: 'Zone' } } });
  registry.registerPath({ method: 'post', path: '/api/parent/push-token', security: [{ parentJwt: [] }],
    request: { body: json(pushTokenSchema) }, responses: { 200: { description: 'OK' } } });

  const generator = new OpenApiGeneratorV31(registry.definitions);
  built = generator.generateDocument({
    openapi: '3.1.0',
    info: { title: 'FamilyShield API', version: '1.0.0' },
  });
  return built;
}
