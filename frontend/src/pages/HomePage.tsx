import { Link } from 'react-router';

import { useCurrentUser } from '../auth/authContext';

export function HomePage() {
  const user = useCurrentUser();
  return (
    <section aria-labelledby="titulo-inicio">
      <h1 id="titulo-inicio" className="h3">
        Previsão financeira familiar — {user.family.name}
      </h1>
      <p className="lead">
        Quanto a família terá disponível no mês, considerando receitas, pagamentos já comprometidos,
        gastos habituais estimados e uma margem prudente.
      </p>
      <div className="alert alert-info" role="status">
        <strong>Em construção:</strong> nesta etapa estão disponíveis o acesso, as contas e as
        configurações da família. Importação de CSV e previsão chegam nas próximas etapas.
      </div>
      <ul>
        <li>
          <Link to="/contas">Cadastrar conta corrente e cartões</Link>
        </li>
        <li>
          <Link to="/configuracoes">Revisar fuso horário, janela da média e margem</Link>
        </li>
        {user.role === 'ADMIN' && (
          <li>
            <Link to="/membros">Gerenciar membros da família</Link>
          </li>
        )}
      </ul>
    </section>
  );
}
