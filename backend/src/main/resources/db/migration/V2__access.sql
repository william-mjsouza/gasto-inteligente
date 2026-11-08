-- V2: acesso (Etapa 2) — usuários, famílias, associações, configurações, contas e auditoria.
--
-- Decisões:
--   * No MVP cada usuário pertence a exatamente uma família (UNIQUE em family_membership.user_id).
--     O contexto de família da sessão é, portanto, derivado do usuário autenticado no servidor.
--   * E-mail é armazenado normalizado (trim + minúsculas) e é único globalmente.
--   * security_stamp muda quando a senha é trocada ou redefinida; sessões com carimbo antigo, ou de
--     usuário/associação desativados, são revogadas na próxima requisição.
--   * Nomes exibidos usam a collation ICU pt-BR, para ordenação correta independentemente da
--     locale padrão do cluster (a imagem Alpine usa ordenação por bytes).
--   * Tabelas da família expõem UNIQUE (family_id, id) para que tabelas futuras usem chaves
--     estrangeiras compostas e o banco impeça vínculo com objeto de outra família.

CREATE TABLE app_user (
    id              UUID         PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    display_name    VARCHAR(100) COLLATE "pt-BR-x-icu" NOT NULL,
    password_hash   VARCHAR(200) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    security_stamp  UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT app_user_email_normalized CHECK (email = lower(btrim(email)) AND email <> ''),
    CONSTRAINT app_user_display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT app_user_status_valid CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX app_user_email_uk ON app_user (email);

CREATE TABLE family (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(100) COLLATE "pt-BR-x-icu" NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL,
    CONSTRAINT family_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE family_membership (
    id          UUID        PRIMARY KEY,
    family_id   UUID        NOT NULL REFERENCES family (id),
    user_id     UUID        NOT NULL REFERENCES app_user (id),
    role        VARCHAR(16) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL,
    CONSTRAINT family_membership_user_uk UNIQUE (user_id),
    CONSTRAINT family_membership_family_id_uk UNIQUE (family_id, id),
    CONSTRAINT family_membership_role_valid CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT family_membership_status_valid CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX family_membership_family_idx ON family_membership (family_id);

CREATE TABLE family_settings (
    family_id                    UUID         PRIMARY KEY REFERENCES family (id),
    currency                     VARCHAR(3)   NOT NULL,
    timezone                     VARCHAR(64)  NOT NULL,
    average_window_months        SMALLINT     NOT NULL,
    prudent_margin_percent       NUMERIC(5,2) NOT NULL,
    card_payment_offset_months   SMALLINT     NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL,
    version                      BIGINT       NOT NULL,
    CONSTRAINT family_settings_currency_brl CHECK (currency = 'BRL'),
    CONSTRAINT family_settings_window_range CHECK (average_window_months BETWEEN 1 AND 12),
    CONSTRAINT family_settings_margin_range CHECK (prudent_margin_percent BETWEEN 0 AND 100),
    CONSTRAINT family_settings_card_offset_range CHECK (card_payment_offset_months BETWEEN 0 AND 3)
);

CREATE TABLE account (
    id          UUID         PRIMARY KEY,
    family_id   UUID         NOT NULL REFERENCES family (id),
    name        VARCHAR(100) COLLATE "pt-BR-x-icu" NOT NULL,
    kind        VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL,
    CONSTRAINT account_family_id_uk UNIQUE (family_id, id),
    CONSTRAINT account_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT account_kind_valid CHECK (kind IN ('CASH', 'CARD')),
    CONSTRAINT account_status_valid CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX account_family_name_uk ON account (family_id, lower(name));

CREATE TABLE audit_event (
    id              UUID         PRIMARY KEY,
    family_id       UUID         REFERENCES family (id),
    actor_user_id   UUID         REFERENCES app_user (id),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       UUID,
    details         JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX audit_event_family_time_idx ON audit_event (family_id, occurred_at DESC);
