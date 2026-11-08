/** Contratos da API (espelham os DTOs do backend). Valores decimais chegam como string. */

export type Role = 'ADMIN' | 'MEMBER';
export type MemberStatus = 'ACTIVE' | 'DISABLED';
export type AccountKind = 'CASH' | 'CARD';
export type AccountStatus = 'ACTIVE' | 'ARCHIVED';

export interface Me {
  userId: string;
  email: string;
  displayName: string;
  role: Role;
  family: { id: string; name: string };
}

export interface LoginResponse {
  user: Me;
  csrf: { headerName: string; token: string } | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Account {
  id: string;
  name: string;
  kind: AccountKind;
  status: AccountStatus;
  createdAt: string;
  version: number;
}

export interface FamilySettings {
  currency: 'BRL';
  timezone: string;
  averageWindowMonths: number;
  prudentMarginPercent: string;
  cardPaymentOffsetMonths: number;
  version: number;
}

export interface Member {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  role: Role;
  status: MemberStatus;
  createdAt: string;
  version: number;
}
