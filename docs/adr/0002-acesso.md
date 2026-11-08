# ADR 0002 — Acesso: usuários, famílias, sessão, CSRF e isolamento

- Estado: aceita
- Data: 2026-11-08
- Etapa: 2 — Acesso (seção 17 da especificação)

## Contexto

A seção 14 exige Spring Security com sessão no servidor, CSRF ativo, login pelo mecanismo do
Spring com respostas JSON, sem registro público, papéis ADMIN/MEMBER e isolamento entre famílias
validado no servidor, além de testes de isolamento antes de conectar dados reais.

## Decisões

### Modelo (migração V2)

- `app_user`, `family`, `family_membership`, `family_settings`, `account`, `audit_event`.
- **Uma família por usuário no MVP** — ver a seção dedicada abaixo.
- E-mail normalizado (trim + minúsculas) e único globalmente, garantido por `CHECK` e índice.
- `UNIQUE (family_id, id)` em `account` e `family_membership` para permitir chaves estrangeiras
  compostas nas próximas etapas: o banco impedirá vínculo com objeto de outra família.
- Concorrência otimista (`version`) em todos os cadastros editáveis; versão desatualizada → 409.
- Nomes com collation ICU `pt-BR`: a imagem Alpine do PostgreSQL ordena por bytes por padrão.
- Membros não são excluídos, apenas desativados, para preservar a auditoria.

### Decisão explícita: cada usuário pertence a exatamente uma família

**Decisão.** No MVP, um usuário (e-mail) tem no máximo uma associação (`family_membership`), o que
é imposto no banco por `UNIQUE (user_id)` e na aplicação: o cadastro de membro recusa e-mail já
existente (409 `EMAIL_UNAVAILABLE`, com mensagem que não revela a qual família ele pertence).

**Motivos.**

- A especificação (seção 1) pede "acesso aos dados de uma família"; não há caso de uso de uma
  pessoa administrando várias famílias.
- A família ativa passa a ser um fato derivado do usuário autenticado, sem estado adicional na
  sessão nem seletor no frontend. Isso elimina uma classe de falhas de isolamento: não existe
  parâmetro, cabeçalho ou estado de cliente que escolha a família.
- Reduz a superfície a testar nas etapas seguintes (importação, exportação, previsão), em que toda
  consulta usa o `familyId` do principal.

**Alternativa descartada.** Várias associações por usuário com "família ativa" escolhida na sessão.
Exigiria endpoint de troca de família, revalidação da associação escolhida a cada requisição,
regras para convites entre famílias e testes de isolamento adicionais, sem benefício no MVP.

**Consequências.**

- Uma pessoa que precise participar de duas famílias precisa de dois e-mails distintos.
- Remover alguém de uma família é feito por desativação (a associação permanece para auditoria);
  portanto, o mesmo e-mail também não pode ser reaproveitado em outra família.
- Para suportar várias famílias no futuro: remover `UNIQUE (user_id)` (substituir por
  `UNIQUE (family_id, user_id)`), guardar a associação escolhida no principal da sessão (o
  `membershipId` já está lá), adicionar troca explícita com rotação de sessão e revalidação, e
  estender os testes de isolamento. O restante do código já filtra por `familyId` do principal.

### Autenticação e sessão

- Login por `formLogin` do Spring Security em `POST /api/v1/auth/login`
  (`application/x-www-form-urlencoded`, campos `email` e `password`), com handlers JSON.
  Falhas sempre respondem `401 INVALID_CREDENTIALS` com a mesma mensagem, para e-mail
  inexistente, senha errada ou acesso desativado.
- `DaoAuthenticationProvider` padrão com `DelegatingPasswordEncoder` (BCrypt). Senha de 12 a
  72 bytes; acima de 72 bytes é rejeitada em vez de truncada (limite do BCrypt).
- Troca do identificador de sessão no login (proteção contra fixação); logout invalida a sessão
  e apaga o cookie. Sessões em memória (instância única); reiniciar encerra as sessões.
- **Revalidação por requisição:** um filtro consulta o estado atual da associação a cada
  requisição autenticada. Associação/usuário desativado ou senha trocada (`security_stamp`
  diferente) → sessão invalidada e 401. Papel alterado → permissões da sessão atualizadas na hora.
  Custo: uma consulta por chave primária por requisição, aceitável no MVP.
- **Limite de tentativas** em memória: 5 falhas por e-mail ou 30 por IP em 15 minutos bloqueiam o
  login (429), mesmo com a senha correta, até a janela expirar. Configurável por
  `app.security.login.*`. Atrás de proxy, o IP só será o do cliente se o proxy for configurado
  (`server.forward-headers-strategy`) — registrar na implantação (Etapa 8).

### CSRF

- Token guardado na sessão do servidor (`HttpSessionCsrfTokenRepository`), não em cookie, com o
  tratamento padrão do Spring Security contra BREACH (token mascarado por requisição).
- Entregue ao React por `GET /api/v1/auth/csrf` (`headerName` + `token`) e enviado no cabeçalho
  `X-CSRF-TOKEN`. O login devolve o token renovado no corpo; após logout o cliente busca outro.
- O cliente refaz uma única vez uma requisição rejeitada com `CSRF_INVALID`, após buscar novo
  token; um 401 dispara o fluxo de sessão expirada.

### Autorização e isolamento

- `/api/v1/members/**` exige `ROLE_ADMIN` na configuração HTTP e novamente no serviço.
- MEMBER gerencia finanças (contas, configurações), mas não acessos.
- Toda consulta de recurso da família usa `findByIdAndFamilyId` com o `familyId` do principal;
  recurso de outra família responde 404 (indistinguível de inexistente).
- Propriedades JSON desconhecidas são rejeitadas (`fail-on-unknown-properties`), de modo que um
  `familyId` enviado pelo cliente resulta em 400, e não é ignorado silenciosamente.
- Regra do último administrador: rebaixar ou desativar o último ADMIN ativo → 409 `LAST_ADMIN`,
  com bloqueio pessimista da família para evitar corrida entre dois administradores.

### Criação de família e recuperação de acesso

- Sem endpoint HTTP. Comando administrativo na própria aplicação, executado sem servidor web:
  `--admin.command=create-family` e `--admin.command=recover-access`. A senha vem de
  `ADMIN_PASSWORD` ou do console, nunca de argumento de linha de comando. Não há credenciais
  padrão no código.
- `recover-access` redefine a senha, reativa usuário e associação, garante ADMIN e revoga as
  sessões existentes.

### Auditoria

- `audit_event` registra criação de família, login, logout, criação/alteração de membro, troca e
  redefinição de senha, alterações de contas e configurações. Detalhes contêm apenas metadados
  (por exemplo, papel anterior e novo); nunca senha, hash, token ou cookie.

## Consequências

- Os testes de integração usam um cliente HTTP real com cookies contra o servidor embarcado e
  PostgreSQL real, cobrindo sessão, CSRF e isolamento como o navegador os vê.
- A interface de auditoria (consulta de eventos) fica para etapas posteriores.
- O contador de tentativas e as sessões são perdidos ao reiniciar; documentado no README.
