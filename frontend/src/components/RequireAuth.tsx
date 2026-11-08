import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';

import { useAuth } from '../auth/authContext';
import { LoadingState } from './Feedback';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const location = useLocation();

  if (state.status === 'loading') {
    return <LoadingState label="Verificando sessão…" />;
  }
  if (state.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  if (state.status !== 'authenticated' || state.user.role !== 'ADMIN') {
    return (
      <div className="alert alert-warning" role="alert">
        <strong>Acesso restrito:</strong> somente administradores da família podem gerenciar membros.
      </div>
    );
  }
  return children;
}
