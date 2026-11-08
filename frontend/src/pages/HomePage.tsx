export function HomePage() {
  return (
    <section aria-labelledby="titulo-inicio">
      <h1 id="titulo-inicio" className="h3">
        Previsão financeira familiar
      </h1>
      <p className="lead">
        Quanto a família terá disponível no mês, considerando receitas, pagamentos já comprometidos,
        gastos habituais estimados e uma margem prudente.
      </p>
      <div className="alert alert-info" role="status">
        <strong>Em construção:</strong> esta é a fundação do projeto. Login, importação de CSV e
        previsão serão disponibilizados nas próximas etapas.
      </div>
    </section>
  );
}
