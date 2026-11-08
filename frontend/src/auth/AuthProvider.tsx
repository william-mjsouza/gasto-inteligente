import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';

import { ApiError, apiRequest, clearCsrfToken, onSessionExpired, setCsrfToken } from '../api/client';
import type { LoginResponse, Me } from '../api/types';
import { AuthContext, type AuthContextValue, type AuthState } from './authContext';

const SESSION_EXPIRED = 'Sua sessão expirou ou foi encerrada. Entre novamente.';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  useEffect(() => {
    let active = true;
    apiRequest<Me>('/api/v1/auth/me', { skipSessionExpired: true })
      .then((user) => {
        if (active) setState({ status: 'authenticated', user });
      })
      .catch(() => {
        if (active) setState({ status: 'anonymous', notice: null });
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(
    () =>
      onSessionExpired(() => {
        // Ao perder a sessão, os dados da tela anterior são descartados (as rotas protegidas
        // desmontam) antes de qualquer outro usuário entrar.
        setState({ status: 'anonymous', notice: SESSION_EXPIRED });
      }),
    [],
  );

  const login = useCallback(async (email: string, password: string) => {
    // O token CSRF é renovado pelo servidor no login; descartamos o anterior.
    clearCsrfToken();
    const response = await apiRequest<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      form: { email, password },
      skipSessionExpired: true,
    });
    setCsrfToken(response.csrf);
    setState({ status: 'authenticated', user: response.user });
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiRequest<undefined>('/api/v1/auth/logout', { method: 'POST', skipSessionExpired: true });
    } catch (error) {
      if (!(error instanceof ApiError)) throw error;
    } finally {
      clearCsrfToken();
      setState({ status: 'anonymous', notice: null });
    }
  }, []);

  const value = useMemo<AuthContextValue>(() => ({ state, login, logout }), [state, login, logout]);

  return <AuthContext value={value}>{children}</AuthContext>;
}
