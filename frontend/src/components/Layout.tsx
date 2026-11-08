import { Link, Outlet } from 'react-router';

export function Layout() {
  return (
    <>
      <a className="skip-link btn btn-light" href="#conteudo">
        Pular para o conteúdo
      </a>
      <header>
        <nav className="navbar navbar-dark bg-dark" aria-label="Navegação principal">
          <div className="container">
            <Link className="navbar-brand" to="/">
              Gasto Inteligente
            </Link>
          </div>
        </nav>
      </header>
      <main id="conteudo" className="container py-4" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  );
}
