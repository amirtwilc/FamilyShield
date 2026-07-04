export function ok(data: unknown, status = 200): Response {
  return Response.json(data, { status });
}
export function err(code: string, message: string, status: number, details?: unknown): Response {
  return Response.json({ error: { code, message, ...(details !== undefined ? { details } : {}) } }, { status });
}
