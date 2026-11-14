#!/usr/bin/env python3
"""Cria credenciais diretamente no cluster; não imprime nem grava as senhas."""
import base64
import json
import os
import secrets
import subprocess
import sys

if len(sys.argv) != 2:
    sys.exit('Uso: create-secrets.py CONTEXTO_KUBERNETES')
command = [os.environ.get('KUBECTL_BIN', 'kubectl'), '--context', sys.argv[1], '--request-timeout=30s', '-n', 'bibliogest']
required = {'postgres-admin': {'password'}, 'keycloak-admin': {'username', 'password'}, 'pgadmin-admin': {'password'}}
required.update({f'{app}-db': {'username', 'password'} for app in ('acervo', 'reservas', 'keycloak')})


def get_objects(resource, names=()):
    arguments = ['get', resource, *names, '-o', 'json']
    if names:
        arguments.append('--ignore-not-found')
    output = subprocess.check_output(command + arguments).strip()
    # Sem objetos nomeados encontrados, kubectl pode sair com sucesso sem JSON.
    if not output and names:
        return []
    try:
        document = json.loads(output)
    except (ValueError, UnicodeError):
        sys.exit(f'Consulta de {resource} retornou JSON inválido ou vazio; nenhuma nova credencial foi criada.')
    if isinstance(document, dict):
        if isinstance(document.get('items'), list):
            return document['items']
        # Uma seleção por nome também pode retornar o objeto, sem um List.
        if resource == 'secrets' and document.get('kind') == 'Secret' and document.get('metadata', {}).get('name') in names:
            return [document]
    sys.exit(f'Resposta inesperada ao consultar {resource}; nenhuma nova credencial foi criada.')


by_name = {item['metadata']['name']: item for item in get_objects('secrets', tuple(required))}
# Não gera credenciais novas se existe um PVC sem o Secret original.
pvcs = get_objects('pvc')
if any(pvc['metadata']['name'] == 'data-postgres-0' for pvc in pvcs):
    original_secrets = required.keys() - {'pgadmin-admin'}
    missing = original_secrets - by_name.keys()
    if missing:
        sys.exit('PVC existente sem credenciais originais. Restaure os Secrets do backup; não gere outras senhas.')
if any(pvc['metadata']['name'] == 'pgadmin-data' for pvc in pvcs) and 'pgadmin-admin' not in by_name:
    sys.exit('PVC pgAdmin existente sem credencial original. Restaure o Secret pgadmin-admin.')
for name, keys in required.items():
    if name in by_name:
        if not keys <= by_name[name].get('data', {}).keys():
            sys.exit(f'Secret {name} incompleto; restaure a credencial original')
        if any(not base64.b64decode(by_name[name]['data'][key]) for key in keys):
            sys.exit(f'Secret {name} contém credencial vazia; restaure a credencial original')
        if name.endswith('-db'):
            expected = 'bibliogest_' + name.removesuffix('-db')
            if base64.b64decode(by_name[name]['data']['username']).decode() != expected:
                sys.exit(f'Secret {name} contém usuário diferente do usuário provisionado')
        print(f'Secret {name}: preservado')
        continue
    values = {'password': secrets.token_urlsafe(36)}
    if 'username' in keys:
        values['username'] = 'bootstrap-admin' if name == 'keycloak-admin' else f'bibliogest_{name.removesuffix("-db")}'
    resource = {'apiVersion': 'v1', 'kind': 'Secret', 'metadata': {'name': name, 'namespace': 'bibliogest'}, 'type': 'Opaque', 'stringData': values}
    result = subprocess.run(command + ['create', '-f', '-'], input=json.dumps(resource), text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # O stderr do API server pode conter a representação enviada: não o exponha.
        sys.exit(f'Não foi possível criar {name}; confira RBAC e a conexão com o API server')
    print(f'Secret {name}: criado')
