#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 2 || $# -gt 4 ]]; then
  echo 'Uso: build-images.sh CONTEXTO CONFIG [all|acervo|reservas|frontend] [--push|--import]' >&2
  exit 1
fi
CONTEXT=$1
CONFIG=$(realpath "$2")
COMPONENT=${3:-all}
MODE=${4:---push}
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ORG_DIR=$(cd -- "$SCRIPT_DIR/../../../.." && pwd)
source "$SCRIPT_DIR/common.sh"
case "$COMPONENT" in all|acervo|reservas|frontend) ;; *) echo 'Aplicação inválida' >&2; exit 1;; esac
case "$MODE" in --push|--import) ;; *) echo 'Escolha --push ou --import' >&2; exit 1;; esac
if [[ -n ${CONTAINER_ENGINE:-} ]]; then
  ENGINE=$CONTAINER_ENGINE
elif command -v docker >/dev/null; then
  ENGINE=docker
elif command -v podman >/dev/null; then
  ENGINE=podman
else
  echo 'Instale Docker ou Podman para construir as imagens (ou use deploy com imagens já publicadas).' >&2
  exit 1
fi
command -v "$ENGINE" >/dev/null
TEMP_DIR=$(mktemp -d)
trap 'rm -rf -- "$TEMP_DIR"' EXIT
PYTHONPATH="$SCRIPT_DIR" python3 - "$CONFIG" "$TEMP_DIR/parameters.json" <<'PY'
import json,sys
from render import load_config
config=load_config(sys.argv[1])
for key in ('ACERVO_IMAGE','RESERVAS_IMAGE','FRONTEND_IMAGE'):
    if '@sha256:' in config[key]:
        sys.exit('Para construir, use tags imutáveis na configuração. Digests são aceitos apenas no deploy.')
with open(sys.argv[2],'w') as stream: json.dump(config,stream)
PY
CONFIG="$TEMP_DIR/parameters.json"
if [[ $MODE == --import ]]; then
  NODE_COUNT=$(k get nodes -o json | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["items"]))')
  [[ $NODE_COUNT == 1 ]] || { echo '--import exige cluster de um nó. Em vários nós, use um registry e --push.' >&2; exit 1; }
  CTR_BIN=${RKE2_CTR_BIN:-/var/lib/rancher/rke2/bin/ctr}
  CONTAINERD_ADDRESS=${RKE2_CONTAINERD_ADDRESS:-/run/k3s/containerd/containerd.sock}
  command -v "$CTR_BIN" >/dev/null
fi
for application in acervo reservas frontend; do
  [[ $COMPONENT == all || $COMPONENT == "$application" ]] || continue
  case "$application" in
    acervo) IMAGE=$(read_config ACERVO_IMAGE); SOURCE="$ORG_DIR/microsservices/acervo-api";;
    reservas) IMAGE=$(read_config RESERVAS_IMAGE); SOURCE="$ORG_DIR/microsservices/reservas-api";;
    frontend) IMAGE=$(read_config FRONTEND_IMAGE); SOURCE="$ORG_DIR/front/bibliogest-frontend";;
  esac
  [[ -f "$SOURCE/Dockerfile" ]] || { echo "Repositório ausente: $SOURCE" >&2; exit 1; }
  "$ENGINE" build --tag "$IMAGE" "$SOURCE"
  if [[ $MODE == --push ]]; then
    "$ENGINE" push "$IMAGE"
  else
    "$ENGINE" save --output "$TEMP_DIR/image.tar" "$IMAGE"
    "$CTR_BIN" --address "$CONTAINERD_ADDRESS" --namespace k8s.io images import "$TEMP_DIR/image.tar"
    rm -- "$TEMP_DIR/image.tar"
  fi
done
