import type { ZodSchema } from 'zod';
import { err } from './http';

function validationMessage(flattened: { fieldErrors: Record<string, string[] | undefined>; formErrors: string[] }): string {
  const fieldMessages = Object.entries(flattened.fieldErrors)
    .flatMap(([field, messages]) => (messages ?? []).map((message) => `${field}: ${message}`));
  return [...fieldMessages, ...flattened.formErrors][0] ?? 'Invalid request body';
}

export async function parseBody<T>(req: Request, schema: ZodSchema<T>): Promise<{ data: T } | { response: Response }> {
  let body: unknown;
  try { body = await req.json(); } catch { return { response: err('invalid_json', 'Body is not valid JSON', 400) }; }
  const r = schema.safeParse(body);
  if (!r.success) {
    const details = r.error.flatten();
    return { response: err('validation_error', validationMessage(details), 400, details) };
  }
  return { data: r.data };
}

export function parseQuery<T>(url: URL, schema: ZodSchema<T>): { data: T } | { response: Response } {
  const r = schema.safeParse(Object.fromEntries(url.searchParams));
  if (!r.success) return { response: err('validation_error', 'Invalid query', 400, r.error.flatten()) };
  return { data: r.data };
}
