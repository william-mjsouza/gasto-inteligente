import type { AccountKind, AccountStatus, MemberStatus, Role } from './api/types';

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Administrador',
  MEMBER: 'Membro',
};

export const MEMBER_STATUS_LABEL: Record<MemberStatus, string> = {
  ACTIVE: 'Ativo',
  DISABLED: 'Desativado',
};

export const ACCOUNT_KIND_LABEL: Record<AccountKind, string> = {
  CASH: 'Conta de caixa',
  CARD: 'Cartão de crédito',
};

export const ACCOUNT_STATUS_LABEL: Record<AccountStatus, string> = {
  ACTIVE: 'Ativa',
  ARCHIVED: 'Arquivada',
};

export const CARD_OFFSET_LABEL: Record<number, string> = {
  0: 'No mesmo mês da compra',
  1: 'No mês seguinte (padrão)',
  2: 'Dois meses depois',
  3: 'Três meses depois',
};
