#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
usage() {
  cat <<'HELP'
Uso (execute no servidor RKE2, com sudo se necessário):
  rke2.sh inspect
  rke2.sh deploy CONFIG [all|postgres|databases|keycloak|acervo|reservas|frontend|pgadmin|ingress]
  rke2.sh up CONFIG [--push|--import]  # build das três imagens + deploy completo
  rke2.sh images CONFIG [all|acervo|reservas|frontend] [--push|--import]
  rke2.sh update {acervo|reservas|frontend|keycloak|pgadmin} IMAGEM_VERSIONADA
  rke2.sh tls FULLCHAIN_PEM PRIVATE_KEY_PEM
  rke2.sh verify-apis [all|acervo|reservas]  # leitura: Secrets, pods e saúde JDBC
  rke2.sh pgadmin                    # encaminha para 127.0.0.1:5050
  rke2.sh credentials CONFIG {pgadmin|keycloak|acervo|reservas|postgres}
Variáveis opcionais: KUBECONFIG, RKE2_CONTEXT, KUBECTL_BIN, CONTAINER_ENGINE.
HELP
}
[[ $# -gt 0 ]] || { usage; exit 1; }
case "$1" in -h|--help|help) usage; exit 0;; esac
export KUBECONFIG=${KUBECONFIG:-/etc/rancher/rke2/rke2.yaml}
if [[ -z ${KUBECTL_BIN:-} ]]; then
  if [[ -x /var/lib/rancher/rke2/bin/kubectl ]]; then
    export KUBECTL_BIN=/var/lib/rancher/rke2/bin/kubectl
  else
    export KUBECTL_BIN=kubectl
  fi
fi
[[ -r "$KUBECONFIG" ]] || { echo "Sem leitura de $KUBECONFIG. Execute este script com sudo no servidor RKE2 ou informe KUBECONFIG." >&2; exit 1; }
CONTEXT=${RKE2_CONTEXT:-$("$KUBECTL_BIN" config current-context)}
source "$SCRIPT_DIR/common.sh"
COMMAND=$1
shift
case "$COMMAND" in
  inspect)
    k cluster-info
    k get nodes -o wide
    k get storageclass,ingressclass
    k get pods -n kube-system -o wide
    k get services,ingress -A
    ;;
  deploy)
    [[ $# -ge 1 && $# -le 2 ]] || { usage; exit 1; }
    exec "$SCRIPT_DIR/deploy.sh" "$CONTEXT" "$1" "${2:-all}"
    ;;
  up)
    [[ $# -ge 1 && $# -le 2 ]] || { usage; exit 1; }
    "$SCRIPT_DIR/build-images.sh" "$CONTEXT" "$1" all "${2:---push}"
    exec "$SCRIPT_DIR/deploy.sh" "$CONTEXT" "$1" all
    ;;
  images)
    [[ $# -ge 1 && $# -le 3 ]] || { usage; exit 1; }
    exec "$SCRIPT_DIR/build-images.sh" "$CONTEXT" "$1" "${2:-all}" "${3:---push}"
    ;;
  update)
    [[ $# -eq 2 ]] || { usage; exit 1; }
    exec "$SCRIPT_DIR/update.sh" "$CONTEXT" "$1" "$2"
    ;;
  tls)
    [[ $# -eq 2 ]] || { usage; exit 1; }
    # --server-side evita armazenar a chave na annotation last-applied-configuration.
    k apply -f "$SCRIPT_DIR/../k8s/00-namespace.yaml"
    k -n bibliogest create secret tls bibliogest-tls --cert="$1" --key="$2" --dry-run=client -o json |
      k apply --server-side --field-manager=bibliogest-tls -f -
    ;;
  verify-apis)
    [[ $# -le 1 ]] || { usage; exit 1; }
    exec python3 "$SCRIPT_DIR/verify-api-databases.py" "$CONTEXT" "${1:-all}"
    ;;
  pgadmin)
    [[ $# -eq 0 ]] || { usage; exit 1; }
    exec "$KUBECTL_BIN" --context "$CONTEXT" -n bibliogest port-forward --address 127.0.0.1 service/pgadmin 5050:8080
    ;;
  credentials)
    [[ $# -eq 2 ]] || { usage; exit 1; }
    exec python3 "$SCRIPT_DIR/credentials.py" "$CONTEXT" "$1" "$2"
    ;;
  *) usage; exit 1;;
esac
