#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 ]]; then echo 'Uso: verify-databases.sh CONTEXTO' >&2; exit 1; fi
# Usa o cliente e os Secrets já montados em cada pod; não imprime credenciais.
# Keycloak/APIs não possuem psql: iniciamos um Job temporário com a imagem do PostgreSQL.
CONTEXT=$1
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"
python3 "$SCRIPT_DIR/database-check-job.py" | k -n bibliogest create -f -
k -n bibliogest wait --for=condition=complete job/database-check --timeout=180s
k -n bibliogest logs job/database-check
k -n bibliogest delete job/database-check
