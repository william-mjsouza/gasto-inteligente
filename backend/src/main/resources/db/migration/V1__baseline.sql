-- V1: linha de base do esquema (Etapa 1 — Fundação).
--
-- Esta migração estabelece o histórico do Flyway em um banco vazio e registra as convenções
-- que todas as migrações seguintes devem respeitar. Nenhuma tabela de domínio é criada aqui:
-- usuários, famílias e contas chegam na Etapa 2 (V2+), conforme a seção 17 da especificação.
--
-- Convenções:
--   * Flyway é o único mecanismo de alteração do esquema (spring.jpa.hibernate.ddl-auto=validate).
--   * Migrações já aplicadas nunca são editadas; correções entram como nova versão.
--   * Dinheiro: NUMERIC(19,2). Nunca REAL/DOUBLE PRECISION/MONEY.
--   * Instantes de auditoria: TIMESTAMPTZ em UTC. Datas de negócio: DATE. Meses: DATE no dia 1
--     com CHECK, ou CHAR(7) AAAA-MM com CHECK — decisão registrada na migração que os introduzir.
--   * Todo dado pertencente à família carrega family_id NOT NULL com chave estrangeira. Vínculos
--     entre entidades da família devem impedir referência cruzada entre famílias no próprio banco
--     (por exemplo, chaves estrangeiras compostas por family_id + id), além da validação na API.
--   * Cadastros editáveis carregam coluna version BIGINT para concorrência otimista.
--   * Nenhuma restrição de unicidade global sobre descrição + data + valor de lançamentos.

SELECT 1;
