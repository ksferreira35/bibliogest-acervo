#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo 'Uso: deploy.sh CONTEXTO CONFIG [all|postgres|databases|keycloak|acervo|reservas|frontend|pgadmin|ingress]' >&2
  exit 1
fi
CONTEXT=$1
CONFIG=$(realpath "$2")
COMPONENT=${3:-all}
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
DEPLOY_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
RENDERED=${BIBLIOGEST_RENDER_DIR:-"$DEPLOY_DIR/rendered"}
source "$SCRIPT_DIR/common.sh"
case "$COMPONENT" in
  all|postgres|databases|keycloak|acervo|reservas|frontend|pgadmin|ingress) ;;
  *) echo 'Componente inválido' >&2; exit 1;;
esac
for program in "$KUBECTL_BIN" python3 openssl; do command -v "$program" >/dev/null; done
RENDER_FLAGS=()
case "$COMPONENT" in postgres|databases|pgadmin) ;; *) RENDER_FLAGS+=(--production);; esac
python3 "$SCRIPT_DIR/render.py" "$CONFIG" --output "$RENDERED" "${RENDER_FLAGS[@]}"
CONFIG="$RENDERED/parameters.json"
k get nodes -o wide
if [[ $COMPONENT == all || $COMPONENT == postgres || $COMPONENT == pgadmin ]]; then
  k get storageclass "$(read_config STORAGE_CLASS)"
fi
# Apenas recursos do próprio BiblioGest; não instala/reconfigura o RKE2.
k apply --dry-run=server -f "$RENDERED/00-namespace.yaml"
k apply -f "$RENDERED/00-namespace.yaml"
if [[ $COMPONENT == all || $COMPONENT == ingress ]]; then
  k get ingressclass "$(read_config INGRESS_CLASS)"
  k get pods -n "$(read_config INGRESS_NAMESPACE)" -l "$(read_config INGRESS_LABEL_KEY)=$(read_config INGRESS_LABEL_VALUE)" -o json | python3 -c '
import json,sys
pods=json.load(sys.stdin)["items"]
if not pods: sys.exit("Controller de Ingress não encontrado; ajuste namespace e labels")
if any(p["spec"].get("hostNetwork") for p in pods):
    sys.exit("Ingress usa hostNetwork: valide/adapte NetworkPolicies para os IPs dos nós antes de publicar")
'
  if ! k -n bibliogest get secret bibliogest-tls -o name >/dev/null 2>&1; then
    echo 'Falta bibliogest-tls. Execute: sudo ./scripts/rke2.sh tls /caminho/fullchain.pem /caminho/privkey.pem' >&2
    exit 1
  fi
  k -n bibliogest get secret bibliogest-tls -o json | python3 -c '
import json,base64,sys
s=json.load(sys.stdin)
if s.get("type")!="kubernetes.io/tls": sys.exit("bibliogest-tls deve ser kubernetes.io/tls")
sys.stdout.buffer.write(base64.b64decode(s["data"]["tls.crt"]))
' | openssl x509 -noout -checkend 86400
  for host_key in APP_HOST AUTH_HOST; do
    k -n bibliogest get secret bibliogest-tls -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -checkhost "$(read_config "$host_key")"
  done
fi
FILES=(01-config.yaml 02-network-policies.yaml)
case "$COMPONENT" in
  all) FILES+=(10-postgres.yaml 11-databases.yaml 20-keycloak.yaml 30-acervo.yaml 31-reservas.yaml 40-frontend.yaml 45-pgadmin.yaml 50-ingress.yaml);;
  postgres) FILES+=(10-postgres.yaml);;
  databases) FILES+=(11-databases.yaml);;
  keycloak) FILES+=(20-keycloak.yaml);;
  acervo) FILES+=(30-acervo.yaml);;
  reservas) FILES+=(31-reservas.yaml);;
  frontend) FILES+=(40-frontend.yaml);;
  pgadmin) FILES+=(45-pgadmin.yaml);;
  ingress) FILES+=(50-ingress.yaml);;
esac
MANIFEST_ARGS=()
for file in "${FILES[@]}"; do MANIFEST_ARGS+=(-f "$RENDERED/$file"); done
k apply --dry-run=server "${MANIFEST_ARGS[@]}"
if [[ $COMPONENT == acervo || $COMPONENT == reservas ]]; then
  # Os bancos já existem: a implantação de uma API consome seu Secret sem provisionar/rotacionar credenciais.
  python3 "$SCRIPT_DIR/verify-api-databases.py" "$CONTEXT" "$COMPONENT" --secrets-only
else
  python3 "$SCRIPT_DIR/create-secrets.py" "$CONTEXT"
fi
k apply -f "$RENDERED/01-config.yaml" -f "$RENDERED/02-network-policies.yaml"
selected() { [[ $COMPONENT == all || $COMPONENT == "$1" ]]; }
wait_postgres() { k -n bibliogest rollout status statefulset/postgres --timeout=300s; }
wait_databases() { k -n bibliogest wait --for=condition=complete job/database-init --timeout=300s; }
wait_app() { k -n bibliogest rollout status "deployment/$1" --timeout=900s; }
apply_configmap() {
  k -n bibliogest create configmap "$1" --from-file="$2=$RENDERED/$3" --dry-run=client -o yaml | k apply -f -
}
if selected postgres; then
  k apply -f "$RENDERED/10-postgres.yaml"
  wait_postgres
fi
if selected databases; then
  wait_postgres
  k apply -f "$RENDERED/11-databases.yaml"
  wait_databases
fi
if selected keycloak; then
  wait_postgres; wait_databases
  apply_configmap keycloak-realm bibliogest-realm.json realm-bibliogest.json
  k apply -f "$RENDERED/20-keycloak.yaml"
  wait_app keycloak
fi
for component in acervo reservas; do
  if selected "$component"; then
    wait_postgres; wait_app keycloak
    if [[ $component == acervo ]]; then file=30-acervo.yaml; else file=31-reservas.yaml; fi
    k apply -f "$RENDERED/$file"
    wait_app "$component"
  fi
done
if selected frontend; then
  wait_app keycloak; wait_app acervo; wait_app reservas
  k apply -f "$RENDERED/40-frontend.yaml"
  wait_app frontend
fi
if selected pgadmin; then
  wait_postgres; wait_databases
  apply_configmap pgadmin-servers servers.json pgadmin-servers.json
  k apply -f "$RENDERED/45-pgadmin.yaml"
  wait_app pgadmin
fi
if selected ingress; then
  wait_app keycloak; wait_app acervo; wait_app reservas; wait_app frontend
  k apply -f "$RENDERED/50-ingress.yaml"
fi
k -n bibliogest get pods,services,pvc,ingress
printf 'Implantação concluída: %s.\n' "$COMPONENT"
if selected pgadmin; then
  echo 'Abra o túnel com: sudo ./scripts/rke2.sh pgadmin'
  echo 'Consulte o login em terminal privado: sudo ./scripts/rke2.sh credentials CONFIG pgadmin'
fi
