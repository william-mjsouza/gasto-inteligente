# Gasto Inteligente — previsão financeira familiar

MVP que responde: **quanto a família terá disponível no mês, considerando receitas, pagamentos já
comprometidos, gastos habituais estimados e uma margem prudente?**

A especificação completa (`ESPECIFICACAO_MVP_FINANCAS_FAMILIARES.md`) fica em `private/` e não é
versionada, pois contém valores e nomes derivados das bases reais da família. Solicite-a ao
mantenedor.

## Estado atual

- **Etapa 1 — Fundação:** monorepositório, versões fixadas, Docker Compose, Flyway, health check
  restrito, erros JSON, SPA servida pelo Spring Boot e CI.
- **Etapa 2 — Acesso:** usuários, famílias, associações ADMIN/MEMBER, configurações da família,
  contas de caixa e cartões, login com sessão no servidor, CSRF, limite de tentativas, revogação
  de sessões, isolamento entre famílias, auditoria e comando administrativo de criação de família.
  Decisões em [docs/adr/0002-acesso.md](docs/adr/0002-acesso.md); contrato em
  [docs/api/openapi.yaml](docs/api/openapi.yaml).

Ainda **não** existem importação de CSV, classificação, compromissos nem previsão (etapas 3 a 8).

## Stack e versões fixadas

| Área | Tecnologia | Versão |
|---|---|---|
| Linguagem backend | Java (Eclipse Temurin) | 21 LTS (imagem `21.0.13_9`) |
| Framework | Spring Boot (Spring Framework 7.0.10, Spring Security 7.1.2) | 4.1.2 |
| Build backend | Maven Wrapper 3.3.4 → Maven | 3.10.0 (SHA-256 verificado) |
| Persistência | Spring Data JPA / Hibernate ORM | 7.4.13.Final |
| Banco | PostgreSQL (dev, testes e produção) | 18.6 (`postgres:18.6-alpine3.24`) |
| Driver JDBC | org.postgresql | 42.7.14 |
| Migrações | Flyway (+ `flyway-database-postgresql`) | 12.4.0 |
| Testes de integração | Testcontainers | 2.0.5 |
| Runtime frontend | Node.js LTS (Krypton) | 24.22.2 |
| Frontend | React / React DOM | 19.3.1 |
| Roteamento | React Router | 8.5.0 |
| Build | Vite / @vitejs/plugin-react | 8.3.5 / 6.1.2 |
| Linguagem frontend | TypeScript (`strict`) | 6.0.3 |
| Interface | Bootstrap | 5.3.8 |
| Testes frontend | Vitest / Testing Library React / jsdom | 5.0.5 / 16.3.3 / 30.1.3 |
| Lint | ESLint / typescript-eslint | 10.13.0 / 8.72.0 |

Versões exatas: [backend/pom.xml](backend/pom.xml) (gerenciadas pelo `spring-boot-starter-parent`),
[frontend/package-lock.json](frontend/package-lock.json) (`save-exact`), [Dockerfile](Dockerfile) e
[compose.yaml](compose.yaml). Justificativas em [docs/adr/0001-fundacao.md](docs/adr/0001-fundacao.md).
Chart.js e Playwright serão adicionados nas etapas que os utilizam (7 e 8).

## Estrutura

| Caminho | Conteúdo |
|---|---|
| `backend/` | Projeto Maven (Spring Boot), API e regras de negócio |
| `backend/src/main/resources/db/migration/` | Migrações Flyway versionadas |
| `frontend/` | Aplicação React + TypeScript (Vite) e seus testes |
| `docs/` | Decisões de arquitetura (ADR) e, futuramente, contrato da API e guia de uso |
| `fixtures/synthetic/` | CSVs fictícios para testes (a partir da Etapa 3) |
| `infra/` | Implantação e backup (Etapa 8) |
| `private/` | **Ignorado pelo Git.** CSVs reais, PDFs e dumps locais |
| `.github/workflows/` | Pipeline de validação |
| `compose.yaml` | Ambiente local completo |
| `AGENTS.md` | Regras e comandos para agentes de desenvolvimento |

## Pré-requisitos

- JDK 21 (o Maven é baixado pelo wrapper; não precisa instalá-lo).
- Node.js 24 LTS (`frontend/.nvmrc`). O `engine-strict` rejeita outras versões principais.
- Docker com Docker Compose v2 (obrigatório para os testes de integração e para o ambiente local).

## Configuração

```bash
cp .env.example .env
```

Edite `.env` e defina `POSTGRES_PASSWORD` (por exemplo, `openssl rand -base64 24`). O `.env` é
ignorado pelo Git e é lido pelo Compose e pelo backend executado a partir de `backend/`.

## Executar o ambiente completo (distribuição)

```bash
docker compose up --build
```

Aplicação em <http://localhost:8080> (ou `APP_PORT`). O build multistage compila o frontend, o
empacota no Spring Boot e roda uma única instância com o PostgreSQL. As portas são publicadas
somente em `127.0.0.1`.

```bash
docker compose down
```

Para apagar também o volume do banco: `docker compose down -v` (destrói os dados locais).

## Criar a família e o primeiro administrador

Não há cadastro público. A família e o primeiro administrador são criados por um comando
administrativo da própria aplicação, executado sem servidor web. A senha **nunca** é passada como
argumento: vem da variável `ADMIN_PASSWORD` ou é digitada no terminal (mínimo de 12 caracteres).

```bash
docker compose up -d db
docker compose run --rm app --spring.main.web-application-type=none --admin.command=create-family "--admin.family-name=Família Exemplo" --admin.email=admin@exemplo.test "--admin.display-name=Administrador"
```

Fora do Docker, a partir de `backend/` (com o `.env` configurado), o equivalente é
`./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.web-application-type=none --admin.command=create-family ..."`.

Depois de entrar, o administrador cadastra os demais membros em **Membros** (com senha inicial,
comunicada por canal seguro). Membros gerenciam as finanças, mas não administram acessos.

### Recuperação administrativa de acesso

Se o administrador perder a senha ou for desativado, quem tem acesso ao servidor executa:

```bash
docker compose run --rm app --spring.main.web-application-type=none --admin.command=recover-access --admin.email=admin@exemplo.test
```

O comando redefine a senha (pedida no terminal ou lida de `ADMIN_PASSWORD`), reativa o usuário e
a associação, garante o papel de administrador e revoga as sessões abertas desse usuário. O evento
fica registrado na auditoria.

## Desenvolvimento

1. Banco:

   ```bash
   docker compose up -d db
   ```

2. Backend (porta 8080), a partir de `backend/`:

   ```bash
   ./mvnw spring-boot:run
   ```

   Alternativa sem Compose, com PostgreSQL efêmero via Testcontainers: `./mvnw spring-boot:test-run`.

3. Frontend (porta 5173), a partir de `frontend/`:

   ```bash
   npm ci
   npm run dev
   ```

   O Vite encaminha `/api` para `http://localhost:8080` (configurável por `BACKEND_URL`); o
   navegador trabalha com uma única origem.

## Testes e verificações

Backend (precisa de Docker em execução; usa PostgreSQL real via Testcontainers, nunca H2):

```bash
cd backend && ./mvnw verify
```

O `verify` também roda o Maven Enforcer: Java 21, Maven ≥ 3.9, nenhuma dependência SNAPSHOT,
versões de plugins explícitas e convergência de dependências.

Frontend:

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

## Endpoints operacionais

| Rota | Acesso | Conteúdo |
|---|---|---|
| `GET /actuator/health` | Público | Apenas `status` (sem detalhes nem componentes) |
| `GET /actuator/health/liveness`, `/readiness` | Público | Sondas para orquestração e `HEALTHCHECK` |
| Demais `/actuator/**` | Negado | Não expostos |
| `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` | Público (CSRF obrigatório nos POST) | Sessão |
| `/api/v1/members/**` | ADMIN | Administração de membros |
| Demais `/api/**` | Autenticado | Ver [docs/api/openapi.yaml](docs/api/openapi.yaml) |
| Demais `GET` | Público | Arquivos da SPA; rotas sem extensão devolvem `index.html` |

Erros da API seguem o formato `{"code", "message", "details", "requestId"}`; rotas `/api`
inexistentes respondem JSON `404`, nunca o HTML da SPA. Todo response traz `X-Request-Id`
(gerado no servidor).

## Comportamentos documentados

- **Sessões em memória:** a instância única do MVP guarda sessões em memória; reiniciar a
  aplicação encerra as sessões e exige novo login. Não há Redis por decisão da especificação.
- **Cookie de sessão:** `HttpOnly`, `SameSite=Lax`, `Secure` controlado por
  `SESSION_COOKIE_SECURE` (padrão `true`; use `false` apenas em `http://localhost`).
- **CSRF** ativo em toda operação mutável, inclusive login e logout. O token fica na sessão do
  servidor, é obtido em `GET /api/v1/auth/csrf` e enviado no cabeçalho `X-CSRF-TOKEN`; é renovado
  no login (devolvido no corpo da resposta) e após o logout.
- **Login:** troca o identificador de sessão; falhas têm mensagem genérica. Após 5 falhas por
  e-mail (ou 30 por IP) em 15 minutos, o login fica bloqueado até a janela expirar (HTTP 429).
  Os contadores ficam em memória e zeram ao reiniciar. Ajustáveis por
  `app.security.login.max-failures-per-email`, `max-failures-per-ip` e `window`.
- **Revogação imediata:** desativar um membro, redefinir a senha dele ou ele trocar a própria
  senha encerra as outras sessões na próxima requisição; mudança de papel vale na hora.
- **Isolamento:** a família vem sempre da sessão. Recursos de outra família respondem 404;
  campos JSON desconhecidos (como `familyId`) são rejeitados com 400.
- **HTTPS** termina no serviço de hospedagem ou proxy; o contêiner escuta HTTP na porta 8080.

## Solução de problemas

- **Porta ocupada** (`5432`, `8080`): altere `POSTGRES_PORT`/`DB_PORT` e `APP_PORT` no `.env`.
- **Erro `PKIX path building failed` no Maven** em Windows com antivírus que inspeciona HTTPS: o
  certificado raiz do antivírus está no repositório do Windows, mas não no do JDK. Para uso local,
  execute com `MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`. Com Node 24, use
  `NODE_OPTIONS=--use-system-ca`. Não versione essas configurações.

## Limitações e próximos passos

- Etapas 3 a 8 pendentes (ver seção 17 da especificação).
- **Cada usuário (e-mail) pertence a exatamente uma família** no MVP, imposto no banco. Quem
  participa de duas famílias precisa de dois e-mails. Motivos e caminho de evolução em
  [docs/adr/0002-acesso.md](docs/adr/0002-acesso.md#decisão-explícita-cada-usuário-pertence-a-exatamente-uma-família).
- Não há tela de consulta da auditoria nem recuperação de senha por e-mail (recuperação é
  administrativa).
- Backup/restauração, implantação, separação de credenciais de migração e revisão de segurança:
  Etapa 8 ([infra/](infra/README.md)).
- Proxy reverso: para o limite por IP usar o IP real do cliente, configure
  `server.forward-headers-strategy` na implantação (Etapa 8).

## Licença

Ver [LICENSE](LICENSE).
