/**
 * Cliente HTTP centralizado: mesma origem, sessão por cookie do servidor (nunca localStorage),
 * token CSRF obtido do endpoint dedicado e enviado no cabeçalho em operações mutáveis, e erros
 * da API convertidos em {@link ApiError}.
 */

export interface ApiErrorDetail {
  field: string | null;
  row: number | null;
  message: string;
}

interface ApiErrorBody {
  code: string;
  message: string;
  details?: ApiErrorDetail[];
  requestId?: string | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: ApiErrorDetail[];
  readonly requestId: string | null;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code;
    this.details = body.details ?? [];
    this.requestId = body.requestId ?? null;
  }
}

export interface CsrfToken {
  headerName: string;
  token: string;
}

type Listener = () => void;

let csrf: CsrfToken | null = null;
let csrfRequest: Promise<CsrfToken> | null = null;
const sessionExpiredListeners = new Set<Listener>();

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** Registra callback chamado quando uma requisição autenticada recebe 401. */
export function onSessionExpired(listener: Listener): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

export function setCsrfToken(token: CsrfToken | null): void {
  csrf = token;
}

/** Descarta o token; o próximo envio mutável busca um novo (necessário após login e logout). */
export function clearCsrfToken(): void {
  csrf = null;
  csrfRequest = null;
}

/** fetch que converte falha de rede (servidor fora do ar, sem conexão) em ApiError legível. */
async function fetchOrNetworkError(input: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(input, init);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      message:
        'Não foi possível conectar ao servidor. Verifique sua conexão e se a aplicação está em execução.',
    });
  }
}

async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await fetchOrNetworkError('/api/v1/auth/csrf', {
    credentials: 'same-origin',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as CsrfToken;
}

async function ensureCsrfToken(): Promise<CsrfToken> {
  if (csrf) {
    return csrf;
  }
  csrfRequest ??= fetchCsrfToken().finally(() => {
    csrfRequest = null;
  });
  csrf = await csrfRequest;
  return csrf;
}

async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('json')) {
    try {
      const body = (await response.json()) as Partial<ApiErrorBody>;
      if (typeof body.code === 'string' && typeof body.message === 'string') {
        return new ApiError(response.status, body as ApiErrorBody);
      }
    } catch {
      // Corpo inválido: cai no erro genérico abaixo.
    }
  }
  return new ApiError(response.status, {
    code: response.status >= 500 ? 'INTERNAL_ERROR' : 'UNEXPECTED_RESPONSE',
    message: 'Não foi possível concluir a operação. Tente novamente.',
  });
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  json?: unknown;
  form?: Record<string, string>;
  signal?: AbortSignal;
  /** Não dispara o aviso de sessão expirada em 401 (login e verificação inicial). */
  skipSessionExpired?: boolean;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return send<T>(path, options, false);
}

async function send<T>(path: string, options: RequestOptions, isRetry: boolean): Promise<T> {
  const method = options.method ?? 'GET';
  const headers = new Headers({ Accept: 'application/json' });
  let body: BodyInit | undefined;
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(options.json);
  } else if (options.form) {
    headers.set('Content-Type', 'application/x-www-form-urlencoded');
    body = new URLSearchParams(options.form).toString();
  }
  if (MUTATING.has(method)) {
    const token = await ensureCsrfToken();
    headers.set(token.headerName, token.token);
  }

  const init: RequestInit = { method, headers, credentials: 'same-origin', cache: 'no-store' };
  if (body !== undefined) {
    init.body = body;
  }
  if (options.signal) {
    init.signal = options.signal;
  }
  const response = await fetchOrNetworkError(path, init);

  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  const error = await toApiError(response);
  if (error.code === 'CSRF_INVALID' && !isRetry) {
    // Token desatualizado (por exemplo, a sessão mudou): busca outro e tenta uma única vez.
    clearCsrfToken();
    return send<T>(path, options, true);
  }
  if (error.status === 401 && !options.skipSessionExpired) {
    clearCsrfToken();
    sessionExpiredListeners.forEach((listener) => {
      listener();
    });
  }
  throw error;
}
