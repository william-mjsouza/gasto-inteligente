import { type SubmitEvent, useState } from 'react';

import { apiRequest } from '../api/client';
import { useApiData } from '../api/useApiData';
import type { Account, AccountKind, Page } from '../api/types';
import { EmptyState, ErrorAlert, LoadingState, SuccessMessage } from '../components/Feedback';
import { ACCOUNT_KIND_LABEL, ACCOUNT_STATUS_LABEL } from '../labels';

export function AccountsPage() {
  const { data: page, error: loadError, reload } = useApiData<Page<Account>>('/api/v1/accounts?size=100');
  const accounts = page?.items ?? null;
  const [actionError, setActionError] = useState<unknown>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [kind, setKind] = useState<AccountKind>('CASH');
  const [busy, setBusy] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');

  const run = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    setActionError(null);
    setSuccess(null);
    try {
      await action();
      setSuccess(message);
      return true;
    } catch (error) {
      setActionError(error);
      return false;
    } finally {
      setBusy(false);
      // Recarrega sempre: após um conflito de versão, mostra o estado atual.
      reload();
    }
  };

  const handleCreate = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    void run(
      () => apiRequest('/api/v1/accounts', { method: 'POST', json: { name, kind } }),
      'Conta cadastrada.',
    ).then((ok) => {
      if (ok) setName('');
    });
  };

  const handleRename = (event: SubmitEvent<HTMLFormElement>, account: Account) => {
    event.preventDefault();
    void run(
      () =>
        apiRequest(`/api/v1/accounts/${account.id}`, {
          method: 'PATCH',
          json: { name: editName, version: account.version },
        }),
      'Nome atualizado.',
    ).then((ok) => {
      if (ok) setEditingId(null);
    });
  };

  const toggleStatus = (account: Account) => {
    const status = account.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE';
    void run(
      () =>
        apiRequest(`/api/v1/accounts/${account.id}`, {
          method: 'PATCH',
          json: { status, version: account.version },
        }),
      status === 'ARCHIVED' ? 'Conta arquivada.' : 'Conta reativada.',
    );
  };

  const remove = (account: Account) => {
    if (!window.confirm(`Excluir a conta "${account.name}"? Esta ação não pode ser desfeita.`)) return;
    void run(
      () =>
        apiRequest(`/api/v1/accounts/${account.id}?version=${String(account.version)}`, {
          method: 'DELETE',
        }),
      'Conta excluída.',
    );
  };

  return (
    <section aria-labelledby="titulo-contas">
      <h1 id="titulo-contas" className="h3">
        Contas e cartões
      </h1>
      <p>
        Cadastre a conta corrente usada para Pix e débito e os cartões de crédito. Dados bancários
        (agência, número) não são necessários.
      </p>

      <form className="row g-2 align-items-end mb-4" onSubmit={handleCreate} aria-label="Nova conta">
        <div className="col-12 col-md-6">
          <label htmlFor="conta-nome" className="form-label">
            Nome
          </label>
          <input
            id="conta-nome"
            className="form-control"
            required
            maxLength={100}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
            }}
          />
        </div>
        <div className="col-12 col-md-3">
          <label htmlFor="conta-tipo" className="form-label">
            Tipo
          </label>
          <select
            id="conta-tipo"
            className="form-select"
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as AccountKind);
            }}
          >
            <option value="CASH">{ACCOUNT_KIND_LABEL.CASH}</option>
            <option value="CARD">{ACCOUNT_KIND_LABEL.CARD}</option>
          </select>
        </div>
        <div className="col-12 col-md-3">
          <button type="submit" className="btn btn-primary w-100" disabled={busy}>
            Cadastrar
          </button>
        </div>
      </form>

      <SuccessMessage message={success} />
      <ErrorAlert error={actionError} />
      <ErrorAlert error={loadError} />

      {accounts === null && !loadError && <LoadingState label="Carregando contas…" />}
      {accounts?.length === 0 && <EmptyState>Nenhuma conta cadastrada ainda.</EmptyState>}
      {accounts && accounts.length > 0 && (
        <div className="table-responsive">
          <table className="table align-middle">
            <caption className="visually-hidden">Contas da família</caption>
            <thead>
              <tr>
                <th scope="col">Nome</th>
                <th scope="col">Tipo</th>
                <th scope="col">Situação</th>
                <th scope="col">Ações</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>
                    {editingId === account.id ? (
                      <form
                        className="d-flex gap-2"
                        onSubmit={(e) => {
                          handleRename(e, account);
                        }}
                      >
                        <label className="visually-hidden" htmlFor={`renomear-${account.id}`}>
                          Novo nome para {account.name}
                        </label>
                        <input
                          id={`renomear-${account.id}`}
                          className="form-control form-control-sm"
                          required
                          maxLength={100}
                          value={editName}
                          onChange={(e) => {
                            setEditName(e.target.value);
                          }}
                        />
                        <button type="submit" className="btn btn-sm btn-primary" disabled={busy}>
                          Salvar
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary"
                          onClick={() => {
                            setEditingId(null);
                          }}
                        >
                          Cancelar
                        </button>
                      </form>
                    ) : (
                      account.name
                    )}
                  </td>
                  <td>{ACCOUNT_KIND_LABEL[account.kind]}</td>
                  <td>{ACCOUNT_STATUS_LABEL[account.status]}</td>
                  <td className="d-flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-primary"
                      disabled={busy}
                      onClick={() => {
                        setEditingId(account.id);
                        setEditName(account.name);
                      }}
                    >
                      Renomear<span className="visually-hidden"> {account.name}</span>
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-secondary"
                      disabled={busy}
                      onClick={() => {
                        toggleStatus(account);
                      }}
                    >
                      {account.status === 'ACTIVE' ? 'Arquivar' : 'Reativar'}
                      <span className="visually-hidden"> {account.name}</span>
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-danger"
                      disabled={busy}
                      onClick={() => {
                        remove(account);
                      }}
                    >
                      Excluir<span className="visually-hidden"> {account.name}</span>
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
