import type { ReactNode } from 'react';

import { ApiError } from '../api/client';

export function LoadingState({ label = 'Carregando…' }: { label?: string }) {
  return (
    <div className="d-flex align-items-center gap-2 py-3" role="status" aria-live="polite">
      <span className="spinner-border spinner-border-sm" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="text-body-secondary fst-italic">{children}</p>;
}

export function SuccessMessage({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="alert alert-success" role="status">
      <strong>Pronto:</strong> {message}
    </div>
  );
}

/** Mostra erro da API como texto (mensagem, campos afetados e identificador da requisição). */
export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null;
  const apiError = error instanceof ApiError ? error : null;
  const message = apiError?.message ?? 'Não foi possível concluir a operação. Tente novamente.';
  return (
    <div className="alert alert-danger" role="alert">
      <strong>Erro:</strong> {message}
      {apiError && apiError.details.length > 0 && (
        <ul className="mb-0 mt-2">
          {apiError.details.map((detail, index) => (
            <li key={`${detail.field ?? ''}-${String(index)}`}>
              {detail.field ? `${detail.field}: ` : ''}
              {detail.message}
            </li>
          ))}
        </ul>
      )}
      {apiError?.requestId && (
        <div className="small mt-2">Identificador da requisição: {apiError.requestId}</div>
      )}
    </div>
  );
}
