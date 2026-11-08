import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section aria-labelledby="titulo-nao-encontrado">
      <h1 id="titulo-nao-encontrado" className="h3">
        Página não encontrada
      </h1>
      <p>O endereço acessado não existe.</p>
      <Link to="/">Voltar ao início</Link>
    </section>
  );
}
