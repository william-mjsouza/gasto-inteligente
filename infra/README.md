# Infraestrutura

Configurações de implantação e backup. Conteúdo previsto para a Etapa 8 (seção 17):

- Implantação de uma instância da imagem `Dockerfile` + PostgreSQL 18 gerenciado, com HTTPS
  terminado no provedor ou proxy e banco sem exposição pública.
- Usuário de banco da aplicação com permissões mínimas e, quando viável, credencial separada para
  migrações.
- Rotina de backup e teste de restauração documentado.

Até lá, o único ambiente suportado é o local (`compose.yaml` na raiz).
