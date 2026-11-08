import { vi } from 'vitest';

export interface RecordedRequest {
  method: string;
  url: string;
  headers: Headers;
  body: string | null;
}

type Handler = (request: RecordedRequest) => Response | undefined;

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function noContent(): Response {
  return new Response(null, { status: 204 });
}

export function apiError(status: number, code: string, message = 'erro'): Response {
  return json(status, { code, message, details: [], requestId: 'req-teste' });
}

/**
 * Substitui fetch por um roteador de teste. Cada handler devolve uma resposta ou undefined para
 * passar ao próximo. Requisições sem handler falham o teste.
 */
export function mockFetch(...handlers: Handler[]) {
  const requests: RecordedRequest[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    const request: RecordedRequest = {
      method: init?.method ?? 'GET',
      url,
      headers: new Headers(init?.headers),
      body: typeof init?.body === 'string' ? init.body : null,
    };
    requests.push(request);
    for (const handler of handlers) {
      const response = handler(request);
      if (response) return Promise.resolve(response);
    }
    return Promise.reject(new Error(`Requisição inesperada: ${request.method} ${url}`));
  });
  vi.stubGlobal('fetch', fn);
  return { requests, fn };
}

export function route(method: string, path: string, respond: (r: RecordedRequest) => Response): Handler {
  return (request) =>
    request.method === method && request.url.split('?')[0] === path ? respond(request) : undefined;
}
