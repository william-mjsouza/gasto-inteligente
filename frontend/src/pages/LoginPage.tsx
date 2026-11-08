import { type SubmitEvent, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';

import { useAuth } from '../auth/authContext';
import { ErrorAlert, LoadingState } from '../components/Feedback';

function destination(state: unknown): string {
  if (state && typeof state === 'object' && 'from' in state && typeof state.from === 'string') {
    return state.from.startsWith('/') && !state.from.startsWith('//') ? state.from : '/';
  }
  return '/';
}

export function LoginPage() {
  const { state, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  if (state.status === 'loading') {
    return <LoadingState label="Verificando sessão…" />;
  }
  if (state.status === 'authenticated') {
    return <Navigate to={destination(location.state)} replace />;
  }

  const handleSubmit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    login(email, password)
      .then(() => {
        setPassword('');
        void navigate(destination(location.state), { replace: true });
      })
      .catch((err: unknown) => {
        setPassword('');
        setError(err);
      })
      .finally(() => {
        setSubmitting(false);
      });
  };

  return (
    <section className="mx-auto narrow-form" aria-labelledby="titulo-login">
      <h1 id="titulo-login" className="h3 mb-3">
        Entrar
      </h1>
      {state.notice && (
        <div className="alert alert-warning" role="status">
          <strong>Atenção:</strong> {state.notice}
        </div>
      )}
      <ErrorAlert error={error} />
      <form onSubmit={handleSubmit} noValidate>
        <div className="mb-3">
          <label htmlFor="login-email" className="form-label">
            E-mail
          </label>
          <input
            id="login-email"
            type="email"
            className="form-control"
            autoComplete="username"
            required
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
            }}
          />
        </div>
        <div className="mb-3">
          <label htmlFor="login-senha" className="form-label">
            Senha
          </label>
          <input
            id="login-senha"
            type="password"
            className="form-control"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
            }}
          />
        </div>
        <button type="submit" className="btn btn-primary w-100" disabled={submitting}>
          {submitting ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
      <p className="small text-body-secondary mt-3">
        Não há cadastro público. O acesso é criado pelo administrador da família.
      </p>
    </section>
  );
}
