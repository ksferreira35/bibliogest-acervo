#!/usr/bin/env python3
import json

apps = ('acervo', 'reservas', 'keycloak')
script = '''set -eu
for application in acervo reservas keycloak; do
  case "$application" in
    acervo) PGPASSWORD=$ACERVO_PASSWORD ;;
    reservas) PGPASSWORD=$RESERVAS_PASSWORD ;;
    keycloak) PGPASSWORD=$KEYCLOAK_PASSWORD ;;
  esac
  export PGPASSWORD
  PGUSER=bibliogest_$application
  export PGUSER
  psql -X -q -d "bibliogest_$application" -c 'SELECT current_database()' >/dev/null
  for other in acervo reservas keycloak postgres template1; do
    [ "$other" != "$application" ] || continue
    case "$other" in postgres|template1) database=$other ;; *) database=bibliogest_$other ;; esac
    if psql -X -q -d "$database" -c 'SELECT 1' >/dev/null 2>&1; then
      echo "FALHA: $PGUSER acessou $database" >&2
      exit 1
    fi
  done
  echo "OK: $PGUSER acessa apenas o próprio banco"
done
'''
resource = {'apiVersion': 'batch/v1', 'kind': 'Job', 'metadata': {'name': 'database-check', 'namespace': 'bibliogest'}, 'spec': {'backoffLimit': 0, 'activeDeadlineSeconds': 120, 'ttlSecondsAfterFinished': 3600, 'template': {'metadata': {'labels': {'app': 'database-init'}}, 'spec': {'restartPolicy': 'Never', 'automountServiceAccountToken': False, 'securityContext': {'runAsUser': 999, 'runAsGroup': 999, 'runAsNonRoot': True, 'seccompProfile': {'type': 'RuntimeDefault'}}, 'containers': [{'name': 'check', 'image': 'postgres:17.12-bookworm', 'command': ['sh', '-c', script], 'env': [{'name': 'PGHOST', 'value': 'postgres'}, {'name': 'PGCONNECT_TIMEOUT', 'value': '3'}] + [{'name': app.upper() + '_PASSWORD', 'valueFrom': {'secretKeyRef': {'name': app + '-db', 'key': 'password'}}} for app in apps], 'resources': {'requests': {'cpu': '25m', 'memory': '32Mi'}, 'limits': {'cpu': '100m', 'memory': '64Mi'}}, 'securityContext': {'allowPrivilegeEscalation': False, 'capabilities': {'drop': ['ALL']}}}]}}}}
print(json.dumps(resource))
