#!/usr/bin/env python3
"""Exibe uma credencial solicitada explicitamente, apenas em terminal interativo."""
import argparse
import base64
import json
import os
import subprocess
import sys
from render import load_config

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('context')
parser.add_argument('config')
parser.add_argument('application', choices=['pgadmin', 'keycloak', 'acervo', 'reservas', 'postgres'])
args = parser.parse_args()
if not sys.stdout.isatty():
    parser.exit(1, 'Execute em terminal interativo; credenciais não serão enviadas para arquivos/pipes/logs.\n')
config = load_config(args.config)
secret = {'pgadmin': 'pgadmin-admin', 'keycloak': 'keycloak-admin', 'postgres': 'postgres-admin'}.get(args.application, args.application + '-db')
command = [os.environ.get('KUBECTL_BIN', 'kubectl'), '--context', args.context, '--request-timeout=30s', '-n', 'bibliogest', 'get', 'secret', secret, '-o', 'json']
data = json.loads(subprocess.check_output(command))['data']
username = config['PGADMIN_EMAIL'] if args.application == 'pgadmin' else 'postgres'
if 'username' in data:
    username = base64.b64decode(data['username']).decode()
print('Usuário:', username)
print('Senha:', base64.b64decode(data['password']).decode())
