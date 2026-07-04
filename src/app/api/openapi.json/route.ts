import { buildOpenApiDocument } from '@/lib/openapi/document';
export const runtime = 'nodejs';
export async function GET(_req: Request) { return Response.json(buildOpenApiDocument()); }
