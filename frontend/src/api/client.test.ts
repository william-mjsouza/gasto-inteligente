import { afterEach, describe, expect, it, vi } from 'vitest';

import { apiError, json, mockFetch, noContent, route } from '../test/fetchMock';
import { ApiError, apiRequest, clearCsrfToken, onSessionExpired } from './client';

const CSRF = { headerName: 'X-CSRF-TOKEN', token: 'token-1' };

afterEach(() => {
  clearCsrfToken();
});

describe('apiRequest', () => {
  it('GET não busca token CSRF e usa a mesma origem sem cache', async () => {
    const { requests, fn } = mockFetch(route('GET', '/api/v1/accounts', () => json(200, { items: [] })));

    await apiRequest('/api/v1/accounts');

    expect(requests).toHaveLength(1);
    const init = fn.mock.calls[0]?.[1];
    expect(init?.credentials).toBe('same-origin');
    expect(init?.cache).toBe('no-store');
  });

  it('operações mutáveis buscam o token CSRF uma vez e o enviam no cabeçalho indicado', async () => {
    const { requests } = mockFetch(
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('POST', '/api/v1/accounts', () => json(201, { id: '1' })),
      route('PATCH', '/api/v1/accounts/1', () => json(200, { id: '1' })),
    );

    await apiRequest('/api/v1/accounts', { method: 'POST', json: { name: 'Conta', kind: 'CASH' } });
    await apiRequest('/api/v1/accounts/1', { method: 'PATCH', json: { version: 0 } });

    expect(requests.map((r) => `${r.method} ${r.url}`)).toEqual([
      'GET /api/v1/auth/csrf',
      'POST /api/v1/accounts',
      'PATCH /api/v1/accounts/1',
    ]);
    expect(requests[1]?.headers.get('X-CSRF-TOKEN')).toBe('token-1');
    expect(requests[1]?.headers.get('Content-Type')).toBe('application/json');
    expect(requests[1]?.body).toBe('{"name":"Conta","kind":"CASH"}');
    expect(requests[2]?.headers.get('X-CSRF-TOKEN')).toBe('token-1');
  });

  it('com token rejeitado, busca um novo e repete a requisição uma única vez', async () => {
    let csrfCalls = 0;
    let posts = 0;
    const { requests } = mockFetch(
      route('GET', '/api/v1/auth/csrf', () => {
        csrfCalls += 1;
        return json(200, { headerName: 'X-CSRF-TOKEN', token: `token-${String(csrfCalls)}` });
      }),
      route('POST', '/api/v1/accounts', () => {
        posts += 1;
        return posts === 1 ? apiError(403, 'CSRF_INVALID') : json(201, { id: '1' });
      }),
    );

    await apiRequest('/api/v1/accounts', { method: 'POST', json: {} });

    const sentTokens = requests.filter((r) => r.method === 'POST').map((r) => r.headers.get('X-CSRF-TOKEN'));
    expect(sentTokens).toEqual(['token-1', 'token-2']);
  });

  it('não repete indefinidamente quando o CSRF continua inválido', async () => {
    mockFetch(
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('POST', '/api/v1/accounts', () => apiError(403, 'CSRF_INVALID')),
    );

    await expect(apiRequest('/api/v1/accounts', { method: 'POST', json: {} })).rejects.toMatchObject({
      code: 'CSRF_INVALID',
      status: 403,
    });
  });

  it('401 em requisição autenticada avisa que a sessão expirou', async () => {
    mockFetch(route('GET', '/api/v1/accounts', () => apiError(401, 'UNAUTHENTICATED')));
    const listener = vi.fn();
    const unsubscribe = onSessionExpired(listener);

    await expect(apiRequest('/api/v1/accounts')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledTimes(1);

    unsubscribe();
  });

  it('skipSessionExpired evita o aviso (verificação inicial e login)', async () => {
    mockFetch(route('GET', '/api/v1/auth/me', () => apiError(401, 'UNAUTHENTICATED')));
    const listener = vi.fn();
    const unsubscribe = onSessionExpired(listener);

    await expect(apiRequest('/api/v1/auth/me', { skipSessionExpired: true })).rejects.toMatchObject({
      status: 401,
    });
    expect(listener).not.toHaveBeenCalled();

    unsubscribe();
  });

  it('converte o corpo de erro da API e trata respostas sem JSON', async () => {
    mockFetch(
      route('GET', '/api/v1/a', () =>
        json(409, {
          code: 'VERSION_CONFLICT',
          message: 'Alterado por outra pessoa.',
          details: [{ field: 'version', row: null, message: 'x' }],
          requestId: 'abc',
        }),
      ),
      route('GET', '/api/v1/b', () => new Response('<html>', { status: 502 })),
    );

    const conflict = await apiRequest('/api/v1/a').catch((e: unknown) => e);
    expect(conflict).toMatchObject({ status: 409, code: 'VERSION_CONFLICT', requestId: 'abc' });
    expect((conflict as ApiError).details).toHaveLength(1);

    const gateway = await apiRequest('/api/v1/b').catch((e: unknown) => e);
    expect(gateway).toMatchObject({ status: 502, code: 'INTERNAL_ERROR' });
  });

  it('servidor inacessível vira erro de conexão legível, inclusive ao buscar o CSRF', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );
    const listener = vi.fn();
    const unsubscribe = onSessionExpired(listener);

    await expect(apiRequest('/api/v1/accounts')).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 });
    const error = await apiRequest('/api/v1/auth/login', { method: 'POST', form: {} }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toContain('Não foi possível conectar ao servidor');
    expect(listener).not.toHaveBeenCalled();

    unsubscribe();
  });

  it('formulário de login é enviado URL-encoded com token CSRF', async () => {
    const { requests } = mockFetch(
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('POST', '/api/v1/auth/login', () => noContent()),
    );

    await apiRequest('/api/v1/auth/login', {
      method: 'POST',
      form: { email: 'a@exemplo.test', password: 'senha & especial' },
    });

    const login = requests[1];
    expect(login?.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(login?.body).toBe('email=a%40exemplo.test&password=senha+%26+especial');
    expect(login?.headers.get('X-CSRF-TOKEN')).toBe('token-1');
  });
});
