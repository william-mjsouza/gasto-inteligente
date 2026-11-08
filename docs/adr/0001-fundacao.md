# ADR 0001 — Fundação do monorepositório

- Estado: aceita
- Data: 2026-11-07
- Etapa: 1 — Fundação (seção 17 da especificação)

## Contexto

A especificação (seção 2) define a stack — Java 21, Spring Boot estável, Maven Wrapper,
PostgreSQL, Flyway, React + TypeScript strict + Vite — e exige versões exatas, estáveis e
compatíveis, sem SNAPSHOT, RC ou `latest`, com a mesma versão principal do PostgreSQL em todos os
ambientes e Node LTS compatível com o Vite fixado.

## Decisões

### Versões

Critério: última release estável compatível, publicada **pelo menos duas semanas antes** da data
da fundação (2026-11-07), para reduzir o risco de cadeia de suprimentos e de regressões recentes.
Imagens Docker usam a versão de patch mais recente, por conterem correções de segurança.

| Item | Escolha | Observação |
|---|---|---|
| Spring Boot | 4.1.2 | Release estável padrão do Spring Initializr; gerencia Spring Framework 7.0.10, Security 7.1.2, Hibernate 7.4.13, Flyway 12.4.0, Testcontainers 2.0.5, Jackson 3.1.7, driver PostgreSQL 42.7.14 |
| Maven | 3.10.0 via Wrapper 3.3.4 | `distributionSha256Sum` verificado contra o `.sha512` do Maven Central |
| PostgreSQL | 18.6 (`postgres:18.6-alpine3.24`) | Mesma imagem no Compose e no Testcontainers |
| JDK de build / JRE de execução | `eclipse-temurin:21.0.13_9-jdk-alpine-3.24` / `-jre-alpine-3.24` | |
| Node.js | 24.22.2 LTS (`node:24.22.2-alpine3.24`) | Node 20 chegou ao fim de vida; Vitest 5 e jsdom 30 exigem Node 22.12+ ou 24 |
| Vite | 8.3.5 | 8.3.6 tinha 2 dias; requer Node `^20.19 \|\| >=22.12` |
| TypeScript | **6.0.3** (não 7.0.2) | `typescript-eslint` 8.72 suporta apenas `<6.1`. TS 7 (compilador nativo) será avaliado quando o ecossistema de lint o suportar |
| React Router | 8.5.0 | Pacote único `react-router` (o `react-router-dom` deixou de ser necessário desde a v7) |

Chart.js (Etapa 7), Playwright (Etapa 8) e Apache Commons CSV (Etapa 3) serão adicionados nas
etapas que os utilizam, para não incluir dependências sem uso.

### Backend

- Projeto gerado pelo Spring Initializr (Boot 4.1.2, Java 21, Maven; dependências web MVC,
  validation, security, data-jpa, postgresql, flyway, actuator, testcontainers) e ajustado:
  versão própria `0.1.0` (sem SNAPSHOT), Maven Enforcer (Java 21, Maven ≥ 3.9, sem dependências
  SNAPSHOT, versões de plugin explícitas, convergência de dependências).
- `spring.jpa.hibernate.ddl-auto=validate`: Flyway é o único mecanismo de alteração do esquema.
  `open-in-view=false`; Hibernate grava instantes em UTC.
- **Migração V1 de linha de base** sem tabelas: as primeiras tabelas de domínio (usuários,
  famílias, contas) pertencem à Etapa 2. A V1 cria o histórico do Flyway e documenta as
  convenções de esquema (dinheiro `NUMERIC(19,2)`, `family_id` obrigatório, `version` para
  concorrência otimista, sem unicidade global de lançamentos).
- `UserDetailsServiceAutoConfiguration` excluída para não criar usuário padrão com senha gerada.
  Será substituída pelo cadastro real na Etapa 2.
- Segurança negando por padrão: `/api/**` exige autenticação (sem mecanismo de login, sempre
  `401`); somente `GET` público para os arquivos da SPA; demais métodos fora da API negados; CSRF
  ativo; CSP restritiva (`'self'`), `Referrer-Policy: same-origin`, `X-Frame-Options: DENY`.
- **Health check restrito:** só `health` exposto, `show-details=never`,
  `show-components=never`, sondas liveness/readiness; o restante do Actuator é negado inclusive a
  usuários autenticados.
- Erros JSON padronizados (`ApiError`: `code`, `message`, `details`, `requestId`) também nos
  filtros de segurança. `X-Request-Id` sempre gerado no servidor (cabeçalho do cliente ignorado,
  para evitar injeção nos logs).
- SPA servida pelo Spring Boot: rotas sem extensão fora de `/api` e `/actuator` devolvem
  `index.html`; `/api` inexistente devolve JSON 404.
- Configuração por variáveis de ambiente; a senha do banco não tem valor padrão. O backend lê
  `../.env` opcionalmente quando executado a partir de `backend/`.

### Frontend

- Criado manualmente (sem `create-vite`) para controlar versões exatas: `.npmrc` com
  `save-exact` e `engine-strict`, `.nvmrc` com 24.22.2.
- TypeScript `strict` mais `noUncheckedIndexedAccess`, `noImplicitReturns`, `noUnused*`,
  `verbatimModuleSyntax`. ESLint com `strictTypeChecked` e regra que proíbe
  `dangerouslySetInnerHTML`.
- Proxy do Vite para `/api` com `changeOrigin: false`, preservando a origem única.

### Ambiente e CI

- `Dockerfile` multistage na raiz (Node → JDK → JRE), usuário não root, `HEALTHCHECK` na sonda de
  readiness. Testes não rodam no build da imagem (dependem de Docker via Testcontainers).
- `compose.yaml` publica portas somente em `127.0.0.1`; o Compose falha se `POSTGRES_PASSWORD`
  não estiver definida.
- GitHub Actions: build e testes do backend com Testcontainers; lint, typecheck, testes e build do
  frontend; consistência de versões (imagem do PostgreSQL igual em todos os pontos, nenhuma tag
  `latest`); build da imagem Docker; `npm audit` de dependências de produção; revisão de
  dependências em pull requests; busca de segredos com gitleaks no histórico completo.

## Consequências

- Atualizar a imagem do PostgreSQL exige alterar `compose.yaml`, `backend/pom.xml` e o fallback de
  `TestcontainersConfiguration` — verificado pelo CI.
- `npm audit` reporta vulnerabilidade moderada em `minimatch` usada apenas por ESLint e
  typescript-eslint (ferramentas de desenvolvimento, sem entrada de usuário). O CI bloqueia apenas
  vulnerabilidades altas ou críticas nas dependências de produção; reavaliar ao atualizar o ESLint.
- Actions estão fixadas por tag de versão exata; fixar por SHA de commit é um endurecimento
  recomendado antes da implantação (Etapa 8).
