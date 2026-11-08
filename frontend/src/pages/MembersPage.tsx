import { type SubmitEvent, useState } from 'react';

import { apiRequest } from '../api/client';
import { useApiData } from '../api/useApiData';
import type { Member, Page, Role } from '../api/types';
import { useCurrentUser } from '../auth/authContext';
import { EmptyState, ErrorAlert, LoadingState, SuccessMessage } from '../components/Feedback';
import { MEMBER_STATUS_LABEL, ROLE_LABEL } from '../labels';

export function MembersPage() {
  const me = useCurrentUser();
  const { data: page, error: loadError, reload } = useApiData<Page<Member>>('/api/v1/members?size=100');
  const members = page?.items ?? null;
  const [actionError, setActionError] = useState<unknown>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [initialPassword, setInitialPassword] = useState('');
  const [resetFor, setResetFor] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState('');

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
      reload();
    }
  };

  const handleCreate = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    void run(
      () =>
        apiRequest('/api/v1/members', {
          method: 'POST',
          json: { email, displayName, role, initialPassword },
        }),
      'Membro cadastrado. Informe a senha inicial à pessoa por um canal seguro.',
    ).then((ok) => {
      setInitialPassword('');
      if (ok) {
        setEmail('');
        setDisplayName('');
        setRole('MEMBER');
      }
    });
  };

  const patch = (member: Member, changes: Partial<Pick<Member, 'role' | 'status'>>, message: string) => {
    void run(
      () =>
        apiRequest(`/api/v1/members/${member.id}`, {
          method: 'PATCH',
          json: { ...changes, version: member.version },
        }),
      message,
    );
  };

  const handleReset = (event: SubmitEvent<HTMLFormElement>, member: Member) => {
    event.preventDefault();
    void run(
      () =>
        apiRequest(`/api/v1/members/${member.id}/password`, {
          method: 'POST',
          json: { newPassword },
        }),
      'Senha redefinida. As sessões abertas dessa pessoa foram encerradas.',
    ).then((ok) => {
      setNewPassword('');
      if (ok) setResetFor(null);
    });
  };

  return (
    <section aria-labelledby="titulo-membros">
      <h1 id="titulo-membros" className="h3">
        Membros da família
      </h1>
      <p>
        Administradores gerenciam acessos. Membros gerenciam as finanças, mas não administram
        acessos. Membros não são excluídos: são desativados.
      </p>

      <form className="row g-2 align-items-end mb-4" onSubmit={handleCreate} aria-label="Novo membro">
        <div className="col-12 col-md-3">
          <label htmlFor="membro-email" className="form-label">
            E-mail
          </label>
          <input
            id="membro-email"
            type="email"
            className="form-control"
            required
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
            }}
          />
        </div>
        <div className="col-12 col-md-3">
          <label htmlFor="membro-nome" className="form-label">
            Nome
          </label>
          <input
            id="membro-nome"
            className="form-control"
            required
            maxLength={100}
            value={displayName}
            onChange={(e) => {
              setDisplayName(e.target.value);
            }}
          />
        </div>
        <div className="col-12 col-md-2">
          <label htmlFor="membro-papel" className="form-label">
            Papel
          </label>
          <select
            id="membro-papel"
            className="form-select"
            value={role}
            onChange={(e) => {
              setRole(e.target.value as Role);
            }}
          >
            <option value="MEMBER">{ROLE_LABEL.MEMBER}</option>
            <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
          </select>
        </div>
        <div className="col-12 col-md-2">
          <label htmlFor="membro-senha" className="form-label">
            Senha inicial
          </label>
          <input
            id="membro-senha"
            type="password"
            className="form-control"
            autoComplete="new-password"
            required
            aria-describedby="membro-senha-ajuda"
            value={initialPassword}
            onChange={(e) => {
              setInitialPassword(e.target.value);
            }}
          />
        </div>
        <div className="col-12 col-md-2">
          <button type="submit" className="btn btn-primary w-100" disabled={busy}>
            Cadastrar
          </button>
        </div>
        <div id="membro-senha-ajuda" className="form-text col-12">
          Mínimo de 12 caracteres.
        </div>
      </form>

      <SuccessMessage message={success} />
      <ErrorAlert error={actionError} />
      <ErrorAlert error={loadError} />

      {members === null && !loadError && <LoadingState label="Carregando membros…" />}
      {members?.length === 0 && <EmptyState>Nenhum membro encontrado.</EmptyState>}
      {members && members.length > 0 && (
        <div className="table-responsive">
          <table className="table align-middle">
            <caption className="visually-hidden">Membros da família</caption>
            <thead>
              <tr>
                <th scope="col">Nome</th>
                <th scope="col">E-mail</th>
                <th scope="col">Papel</th>
                <th scope="col">Situação</th>
                <th scope="col">Ações</th>
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.id}>
                  <td>
                    {member.displayName}
                    {member.userId === me.userId && ' (você)'}
                  </td>
                  <td>{member.email}</td>
                  <td>{ROLE_LABEL[member.role]}</td>
                  <td>{MEMBER_STATUS_LABEL[member.status]}</td>
                  <td>
                    <div className="d-flex flex-wrap gap-2">
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-primary"
                        disabled={busy}
                        onClick={() => {
                          const next: Role = member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN';
                          patch(member, { role: next }, 'Papel atualizado.');
                        }}
                      >
                        {member.role === 'ADMIN' ? 'Tornar membro' : 'Tornar administrador'}
                        <span className="visually-hidden"> {member.displayName}</span>
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary"
                        disabled={busy}
                        onClick={() => {
                          const status = member.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
                          patch(
                            member,
                            { status },
                            status === 'DISABLED' ? 'Acesso desativado.' : 'Acesso reativado.',
                          );
                        }}
                      >
                        {member.status === 'ACTIVE' ? 'Desativar' : 'Reativar'}
                        <span className="visually-hidden"> {member.displayName}</span>
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-danger"
                        disabled={busy}
                        onClick={() => {
                          setResetFor(member.id);
                          setNewPassword('');
                        }}
                      >
                        Redefinir senha<span className="visually-hidden"> de {member.displayName}</span>
                      </button>
                    </div>
                    {resetFor === member.id && (
                      <form
                        className="d-flex gap-2 mt-2"
                        onSubmit={(e) => {
                          handleReset(e, member);
                        }}
                      >
                        <label className="visually-hidden" htmlFor={`nova-senha-${member.id}`}>
                          Nova senha de {member.displayName}
                        </label>
                        <input
                          id={`nova-senha-${member.id}`}
                          type="password"
                          autoComplete="new-password"
                          className="form-control form-control-sm"
                          required
                          value={newPassword}
                          onChange={(e) => {
                            setNewPassword(e.target.value);
                          }}
                        />
                        <button type="submit" className="btn btn-sm btn-danger" disabled={busy}>
                          Confirmar
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary"
                          onClick={() => {
                            setResetFor(null);
                          }}
                        >
                          Cancelar
                        </button>
                      </form>
                    )}
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
