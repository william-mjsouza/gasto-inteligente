import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { clearCsrfToken } from './api/client';
import type { Me } from './api/types';
import { App } from './App';
import { apiError, json, mockFetch, noContent, route } from './test/fetchMock';

const ADMIN: Me = {
  userId: 'u-1',
  email: 'admin@exemplo.test',
  displayName: 'Ana Admin',
  role: 'ADMIN',
  family: { id: 'f-1', name: 'Família Exemplo' },
};

const MEMBER: Me = { ...ADMIN, userId: 'u-2', displayName: 'Beto Membro', role: 'MEMBER' };

const CSRF = { headerName: 'X-CSRF-TOKEN', token: 'anon-token' };

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

afterEach(() => {
  clearCsrfToken();
});

describe('acesso', () => {
  it('sem sessão, redireciona para o login sem mostrar dados', async () => {
    mockFetch(route('GET', '/api/v1/auth/me', () => apiError(401, 'UNAUTHENTICATED')));

    renderAt('/contas');

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Contas' })).not.toBeInTheDocument();
  });

  it('login envia credenciais com CSRF, usa o novo token e mostra a família', async () => {
    const { requests } = mockFetch(
      route('GET', '/api/v1/auth/me', () => apiError(401, 'UNAUTHENTICATED')),
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('POST', '/api/v1/auth/login', () =>
        json(200, { user: ADMIN, csrf: { headerName: 'X-CSRF-TOKEN', token: 'novo-token' } }),
      ),
      route('GET', '/api/v1/accounts', () => json(200, { items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 })),
      route('POST', '/api/v1/accounts', () => json(201, { id: 'a-1' })),
    );
    const user = userEvent.setup();
    renderAt('/contas');

    await user.type(await screen.findByLabelText('E-mail'), ADMIN.email);
    await user.type(screen.getByLabelText('Senha'), 'senha-correta-123');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Contas e cartões' })).toBeInTheDocument();
    expect(screen.getByText(/Ana Admin \(Administrador\) — Família Exemplo/)).toBeInTheDocument();
    expect(await screen.findByText('Nenhuma conta cadastrada ainda.')).toBeInTheDocument();

    const login = requests.find((r) => r.url === '/api/v1/auth/login');
    expect(login?.headers.get('X-CSRF-TOKEN')).toBe('anon-token');
    expect(login?.body).toContain('email=admin%40exemplo.test');

    await user.type(screen.getByLabelText('Nome'), 'Conta Corrente');
    await user.click(screen.getByRole('button', { name: 'Cadastrar' }));
    await waitFor(() => {
      expect(requests.some((r) => r.method === 'POST' && r.url === '/api/v1/accounts')).toBe(true);
    });
    const create = requests.find((r) => r.method === 'POST' && r.url === '/api/v1/accounts');
    expect(create?.headers.get('X-CSRF-TOKEN')).toBe('novo-token');
    expect(create?.body).toBe('{"name":"Conta Corrente","kind":"CASH"}');
  });

  it('falha de login mostra mensagem genérica e limpa a senha', async () => {
    mockFetch(
      route('GET', '/api/v1/auth/me', () => apiError(401, 'UNAUTHENTICATED')),
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('POST', '/api/v1/auth/login', () =>
        apiError(401, 'INVALID_CREDENTIALS', 'E-mail ou senha inválidos.'),
      ),
    );
    const user = userEvent.setup();
    renderAt('/login');

    await user.type(await screen.findByLabelText('E-mail'), 'x@exemplo.test');
    await user.type(screen.getByLabelText('Senha'), 'qualquer-senha');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.');
    expect(screen.getByLabelText('Senha')).toHaveValue('');
  });

  it('membro não vê a administração de membros', async () => {
    mockFetch(route('GET', '/api/v1/auth/me', () => json(200, MEMBER)));

    renderAt('/membros');

    expect(await screen.findByRole('alert')).toHaveTextContent('somente administradores');
    const nav = screen.getByRole('navigation', { name: 'Navegação principal' });
    expect(within(nav).queryByRole('link', { name: 'Membros' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Contas' })).toBeInTheDocument();
  });

  it('sessão expirada descarta a tela atual e volta ao login com aviso', async () => {
    let accountsCalls = 0;
    mockFetch(
      route('GET', '/api/v1/auth/me', () => json(200, ADMIN)),
      route('GET', '/api/v1/accounts', () => {
        accountsCalls += 1;
        return accountsCalls === 1
          ? json(200, {
              items: [{ id: 'a-1', name: 'Conta Secreta', kind: 'CASH', status: 'ACTIVE', createdAt: '', version: 0 }],
              page: 0,
              size: 100,
              totalItems: 1,
              totalPages: 1,
            })
          : apiError(401, 'UNAUTHENTICATED');
      }),
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('PATCH', '/api/v1/accounts/a-1', () => apiError(401, 'UNAUTHENTICATED')),
    );
    const user = userEvent.setup();
    renderAt('/contas');

    expect(await screen.findByRole('cell', { name: 'Conta Secreta' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Arquivar/ }));

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.getByText(/Sua sessão expirou/)).toBeInTheDocument();
    expect(screen.queryAllByText(/Conta Secreta/)).toHaveLength(0);
  });

  it('logout encerra a sessão e volta ao login', async () => {
    const { requests } = mockFetch(
      route('GET', '/api/v1/auth/me', () => json(200, ADMIN)),
      route('GET', '/api/v1/auth/csrf', () => json(200, CSRF)),
      route('POST', '/api/v1/auth/logout', () => noContent()),
    );
    const user = userEvent.setup();
    renderAt('/');

    await user.click(await screen.findByRole('button', { name: 'Sair' }));

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(requests.find((r) => r.url === '/api/v1/auth/logout')?.headers.get('X-CSRF-TOKEN')).toBe(
      'anon-token',
    );
  });

  it('rota desconhecida mostra página de não encontrado', async () => {
    mockFetch(route('GET', '/api/v1/auth/me', () => apiError(401, 'UNAUTHENTICATED')));

    renderAt('/rota/que/nao/existe');

    expect(await screen.findByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument();
  });
});
