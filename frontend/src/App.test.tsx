import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { App } from './App';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
  it('renderiza a página inicial em português com landmarks acessíveis', () => {
    renderAt('/');

    expect(
      screen.getByRole('heading', { level: 1, name: 'Previsão financeira familiar' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Em construção');
  });

  it('mostra página de não encontrado para rotas desconhecidas', () => {
    renderAt('/rota/que/nao/existe');

    expect(
      screen.getByRole('heading', { level: 1, name: 'Página não encontrada' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voltar ao início' })).toHaveAttribute('href', '/');
  });
});
