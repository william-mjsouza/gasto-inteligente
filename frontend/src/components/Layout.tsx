import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';

import { useAuth } from '../auth/authContext';
import { ROLE_LABEL } from '../labels';

export function Layout() {
  const { state, logout } = useAuth();
  const [leaving, setLeaving] = useState(false);
  const user = state.status === 'authenticated' ? state.user : null;

  const handleLogout = () => {
    setLeaving(true);
    void logout().finally(() => {
      setLeaving(false);
    });
  };

  return (
    <>
      <a className="skip-link btn btn-light" href="#conteudo">
        Pular para o conteúdo
      </a>
      <header>
        <nav className="navbar navbar-expand-md navbar-dark bg-dark" aria-label="Navegação principal">
          <div className="container flex-wrap gap-2">
            <Link className="navbar-brand" to="/">
              Gasto Inteligente
            </Link>
            {user && (
              <>
                <ul className="navbar-nav flex-row flex-wrap gap-3 me-auto">
                  <li className="nav-item">
                    <NavLink className="nav-link" to="/" end>
                      Início
                    </NavLink>
                  </li>
                  <li className="nav-item">
                    <NavLink className="nav-link" to="/contas">
                      Contas
                    </NavLink>
                  </li>
                  <li className="nav-item">
                    <NavLink className="nav-link" to="/configuracoes">
                      Configurações
                    </NavLink>
                  </li>
                  {user.role === 'ADMIN' && (
                    <li className="nav-item">
                      <NavLink className="nav-link" to="/membros">
                        Membros
                      </NavLink>
                    </li>
                  )}
                </ul>
                <div className="d-flex align-items-center gap-2 text-light">
                  <span>
                    {user.displayName} ({ROLE_LABEL[user.role]}) — {user.family.name}
                  </span>
                  <button
                    type="button"
                    className="btn btn-outline-light btn-sm"
                    onClick={handleLogout}
                    disabled={leaving}
                  >
                    {leaving ? 'Saindo…' : 'Sair'}
                  </button>
                </div>
              </>
            )}
          </div>
        </nav>
      </header>
      <main id="conteudo" className="container py-4" tabIndex={-1}>
        {/* A chave por usuário garante que nenhum estado de tela sobreviva a troca de sessão. */}
        <Outlet key={user?.userId ?? 'anonimo'} />
      </main>
    </>
  );
}
