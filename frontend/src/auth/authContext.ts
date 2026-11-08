import { createContext, useContext } from 'react';

import type { Me } from '../api/types';

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous'; notice: string | null }
  | { status: 'authenticated'; user: Me };

export interface AuthContextValue {
  state: AuthState;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth deve ser usado dentro de AuthProvider.');
  }
  return value;
}

/** Usuário autenticado; só pode ser usado em rotas protegidas por RequireAuth. */
export function useCurrentUser(): Me {
  const { state } = useAuth();
  if (state.status !== 'authenticated') {
    throw new Error('Usuário não autenticado.');
  }
  return state.user;
}
