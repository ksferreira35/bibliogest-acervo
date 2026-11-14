#!/usr/bin/env python3
"""Confere Secrets, configuração dos pods e saúde do banco; não altera recursos/dados."""
import argparse
import base64
import json
import os
import subprocess
import sys

TARGETS = {'acervo': ('bibliogest_acervo', 'acervo-db'), 'reservas': ('bibliogest_reservas', 'reservas-db')}


def kubectl(context, arguments, operation, as_json=True):
    command = [os.environ.get('KUBECTL_BIN', 'kubectl'), '--context', context,
               '--request-timeout=15s', '-n', 'bibliogest', *arguments]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        # Não encaminha respostas/erros que possam carregar dados de configuração.
        raise ValueError(f'Falha ao {operation}. Confira acesso/RBAC e o estado do recurso no namespace bibliogest.')
    if not as_json:
        return result.stdout
    try:
        return json.loads(result.stdout)
    except (ValueError, UnicodeError):
        raise ValueError(f'Resposta JSON inválida ao {operation}.') from None


def verify_secret(context, secret_name):
    secret = kubectl(context, ['get', 'secret', secret_name, '-o', 'json'], f'consultar o Secret {secret_name}')
    data = secret.get('data', {})
    for key in ('username', 'password'):
        try:
            if not base64.b64decode(data[key], validate=True):
                raise ValueError()
        except (KeyError, ValueError, TypeError):
            raise ValueError(f'Secret {secret_name}: chave {key} ausente, vazia ou inválida.') from None


def verify_container(spec, app):
    database, secret_name = TARGETS[app]
    container = next((item for item in spec.get('containers', []) if item['name'] == app), None)
    if container is None:
        raise ValueError(f'Container {app} não encontrado.')
    env = {item['name']: item for item in container.get('env', [])}
    if env.get('DB_URL', {}).get('value') != f'jdbc:postgresql://postgres:5432/{database}':
        raise ValueError(f'{app}: DB_URL não aponta para o banco interno esperado.')
    for variable, key in (('DB_USERNAME', 'username'), ('DB_PASSWORD', 'password')):
        reference = env.get(variable, {}).get('valueFrom', {}).get('secretKeyRef', {})
        if reference.get('name') != secret_name or reference.get('key') != key or reference.get('optional'):
            raise ValueError(f'{app}: {variable} deve usar {secret_name}, chave {key}, obrigatória.')


def verify_api(context, app):
    database, secret_name = TARGETS[app]
    deployment = kubectl(context, ['get', 'deployment', app, '-o', 'json'], f'consultar o Deployment {app}')
    verify_container(deployment['spec']['template']['spec'], app)
    labels = deployment['spec']['selector'].get('matchLabels', {})
    if not labels:
        raise ValueError(f'{app}: selector de pods ausente.')
    selector = ','.join(f'{key}={value}' for key, value in sorted(labels.items()))
    pods = kubectl(context, ['get', 'pods', '-l', selector, '-o', 'json'], f'consultar pods de {app}')['items']
    ready = [pod for pod in pods if not pod['metadata'].get('deletionTimestamp')
             and pod.get('status', {}).get('phase') == 'Running'
             and any(condition.get('type') == 'Ready' and condition.get('status') == 'True'
                     for condition in pod.get('status', {}).get('conditions', []))]
    if not ready:
        raise ValueError(f'{app}: nenhum pod Ready. Faça o deploy/rollout antes desta verificação.')
    for pod in ready:
        verify_container(pod['spec'], app)
        # A imagem atual usa Alpine/BusyBox. A readiness inclui o indicador JDBC db.
        health = kubectl(context, ['exec', pod['metadata']['name'], '-c', app, '--', 'wget', '-q', '-T', '10', '-O', '-',
                                  f'http://127.0.0.1:8080/{app}/actuator/health/readiness'], f'consultar a readiness de {app}')
        if health.get('status') != 'UP':
            raise ValueError(f'{app}: readiness com verificação de banco não está UP.')
    print(f'OK {app}: {secret_name}; postgres:5432/{database}; readiness UP em {len(ready)} pod(s).')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('context')
    parser.add_argument('application', nargs='?', choices=['all', *TARGETS], default='all')
    parser.add_argument('--secrets-only', action='store_true')
    args = parser.parse_args()
    apps = TARGETS if args.application == 'all' else [args.application]
    try:
        # Confere todos os Secrets antes de iniciar qualquer outra consulta.
        for app in apps:
            verify_secret(args.context, TARGETS[app][1])
        for app in apps:
            if args.secrets_only:
                print(f'OK Secret {TARGETS[app][1]}: chaves username/password presentes e não vazias.')
            else:
                verify_api(args.context, app)
    except (ValueError, KeyError, TypeError) as error:
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
