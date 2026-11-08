# AGENTS.md — regras para agentes de desenvolvimento

Leia a especificação `private/ESPECIFICACAO_MVP_FINANCAS_FAMILIARES.md` integralmente antes de
editar. Ela é a fonte dos requisitos; este arquivo resume as regras de trabalho e os comandos.
A especificação **não é versionada** porque contém valores e nomes derivados das bases reais da
família; obtenha-a com o mantenedor e coloque-a em `private/` (ignorado pelo Git). Nunca copie
trechos com nomes de estabelecimentos ou valores reais para arquivos versionados; nos testes, use
nomes sintéticos.

## Estado e escopo

- Etapa concluída: **1 — Fundação**. Implemente uma etapa por vez, na ordem da seção 17, somente
  após aprovação humana da etapa anterior.
- Não antecipe funcionalidades de etapas futuras. Não mude a arquitetura (monólito Spring Boot +
  PostgreSQL + React) nem acrescente microserviços, mensageria ou LLM ao produto.

## Comandos

| Objetivo | Comando (a partir da raiz) |
|---|---|
| Configurar ambiente | `cp .env.example .env` e definir `POSTGRES_PASSWORD` |
| Ambiente completo | `docker compose up --build` |
| Só o banco | `docker compose up -d db` |
| Backend: testes + enforcer | `cd backend && ./mvnw verify` (requer Docker) |
| Backend: executar | `cd backend && ./mvnw spring-boot:run` |
| Frontend: instalar | `cd frontend && npm ci` |
| Frontend: validar | `cd frontend && npm run lint && npm run typecheck && npm test && npm run build` |
| Frontend: executar | `cd frontend && npm run dev` |

## Invariantes que não podem ser quebrados

- **Dinheiro:** `BigDecimal` no Java, `NUMERIC(19,2)` no banco, string decimal com ponto na API
  (`"1234.56"`). Nunca `double`/`float`, nunca cálculo financeiro no React. Arredondamento
  explícito (`HALF_UP` nos valores finais).
- **Datas:** `LocalDate`/`YearMonth` no negócio; auditoria em UTC (`TIMESTAMPTZ`). Timezone padrão
  da família: `America/Fortaleza`.
- **Esquema:** somente Flyway altera o banco (`ddl-auto=validate`). Nunca edite migração já
  versionada; crie `V{n+1}__descricao.sql`.
- **Banco de testes:** PostgreSQL real via Testcontainers, mesma imagem de `compose.yaml`
  (`postgres.image` no `pom.xml`). **Nunca H2.**
- **Isolamento:** `familyId` vem do contexto autenticado no servidor, nunca do cliente. Valide a
  família de todo ID relacionado em escritas e lotes.
- **Segurança:** não desative CSRF, validação, autorização ou testes para contornar erros.
  Negação por padrão. Sem JWT ou credenciais em `localStorage`. Sem `dangerouslySetInnerHTML`.
- **Erros da API:** formato `ApiError` (`code`, `message`, `details`, `requestId`); 400/401/403/
  404/409 conforme a seção 13. Nunca devolver stack trace, SQL, senha ou conteúdo de arquivo.
- **Previsão** não é lançamento: recalcular nunca cria `Transaction`.
- **Pendências explícitas:** diante de ambiguidade, use estado `UNKNOWN`/pendente; não invente
  dados da família para passar em testes. `UNKNOWN` ≠ `NOT_APPLICABLE`.

## Dados e segredos

- Nunca versionar `.env`, credenciais, CSVs familiares reais, PDFs, dumps ou backups. Use
  `private/` (ignorado pelo Git) para arquivos reais locais.
- Fixtures do repositório são apenas sintéticos (`fixtures/synthetic/`), sem nomes ou dados reais.
- Não enviar dados financeiros a serviços externos de IA. Não registrar extratos, senhas, cookies,
  tokens ou conteúdo integral de arquivos nos logs.

## Versões

- Somente releases estáveis: sem SNAPSHOT, RC, beta ou tag `latest` (o Maven Enforcer bloqueia
  SNAPSHOT; o CI bloqueia `latest` em imagens).
- Dependências npm com versão exata (`.npmrc` com `save-exact=true`); sempre commitar
  `package-lock.json`.
- Ao trocar a imagem do PostgreSQL, atualize juntos `compose.yaml`, `backend/pom.xml`
  (`postgres.image`) e o fallback em `TestcontainersConfiguration` — o CI confere.

## Testes e relatórios

- Testes verificam regras de negócio e limites de acesso, não getters ou mocks que copiam a
  implementação. Ao corrigir erro de cálculo, adicione caso de regressão que falharia antes.
- Relate exatamente o que foi executado e o que não foi verificado. Mock não prova integração com
  banco, navegador ou implantação. Revisão por outro agente é complementar, não prova de segurança.

## Organização do backend

Monólito por funcionalidade em `com.gastointeligente`: `auth`, `household`, `imports`,
`transactions`, `classification`, `commitments`, `statements`, `forecasts`. Infraestrutura
transversal em `shared` (`shared.web`, `shared.security`). Separe API, aplicação, domínio e
persistência dentro do módulo quando melhorar a leitura; evite abstrações sem necessidade. O motor
de previsão é puro: recebe dados, configuração e data de referência; relógio injetável.
