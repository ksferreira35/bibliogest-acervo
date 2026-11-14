#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 3 ]]; then
  echo 'Uso: update.sh CONTEXTO {acervo|reservas|frontend|keycloak|pgadmin} IMAGEM_VERSIONADA' >&2
  exit 1
fi
CONTEXT=$1
APP=$2
IMAGE=$3
case "$APP" in acervo|reservas|frontend|keycloak|pgadmin) ;; *) echo 'Aplicação inválida' >&2; exit 1;; esac
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PYTHONPATH="$SCRIPT_DIR" python3 -c 'from render import pinned_image; import sys; pinned_image(sys.argv[1])' "$IMAGE"
source "$SCRIPT_DIR/common.sh"
# Em uma atualização do Keycloak, faça antes backup e leia o guia de migração.
IMAGE_ARGS=("$APP=$IMAGE")
if [[ $APP == pgadmin ]]; then IMAGE_ARGS+=("prepare-database-passwords=$IMAGE"); fi
k -n bibliogest set image "deployment/$APP" "${IMAGE_ARGS[@]}" --dry-run=server -o name
k -n bibliogest set image "deployment/$APP" "${IMAGE_ARGS[@]}"
k -n bibliogest rollout status "deployment/$APP" --timeout=900s
printf '%s\n' 'Atualização aplicada. Registre a imagem no deployment.local.json antes da próxima implantação completa.'
