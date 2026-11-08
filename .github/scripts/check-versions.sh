#!/usr/bin/env bash
# Verifica regras de versão da fundação:
#  1. A imagem do PostgreSQL é a mesma em compose.yaml, backend/pom.xml e TestcontainersConfiguration.
#  2. Nenhuma imagem Docker usa a tag latest ou fica sem tag.
#  3. Nenhuma dependência npm usa faixa de versão, tag latest ou pré-release.
set -euo pipefail

cd "$(dirname "$0")/../.."
fail=0

compose_pg=$(grep -E '^\s*image:\s*postgres:' compose.yaml | sed -E 's/^\s*image:\s*//' | tr -d '[:space:]')
pom_pg=$(sed -nE 's:.*<postgres.image>([^<$]+)</postgres.image>.*:\1:p' backend/pom.xml)
tc_pg=$(sed -nE 's/.*getProperty\("postgres.image", "([^"]+)"\).*/\1/p' \
  backend/src/test/java/com/gastointeligente/TestcontainersConfiguration.java)

echo "compose.yaml:                 ${compose_pg}"
echo "backend/pom.xml:              ${pom_pg}"
echo "TestcontainersConfiguration:  ${tc_pg}"
if [[ -z "$compose_pg" || "$compose_pg" != "$pom_pg" || "$pom_pg" != "$tc_pg" ]]; then
  echo "ERRO: imagens do PostgreSQL divergentes." >&2
  fail=1
fi

images=$( { grep -hE '^\s*FROM\s' Dockerfile | awk '{print $2}'; grep -hE '^\s*image:' compose.yaml | awk '{print $2}'; } )
while read -r image; do
  [[ -z "$image" ]] && continue
  [[ "$image" == gasto-inteligente:* ]] && continue
  if [[ "$image" != *:* || "$image" == *:latest ]]; then
    echo "ERRO: imagem sem versão exata: $image" >&2
    fail=1
  fi
done <<< "$images"

node -e '
  const pkg = require("./frontend/package.json");
  const all = { ...pkg.dependencies, ...pkg.devDependencies };
  const bad = Object.entries(all).filter(([, v]) => !/^\d+\.\d+\.\d+$/.test(v));
  if (bad.length) {
    console.error("ERRO: dependências npm sem versão exata estável:", bad);
    process.exit(1);
  }
' || fail=1

if [[ $fail -ne 0 ]]; then
  exit 1
fi
echo "Versões consistentes."
