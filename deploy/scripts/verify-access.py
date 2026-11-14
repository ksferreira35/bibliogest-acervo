#!/usr/bin/env python3
"""Verifica HTTPS e os dois perfis com access tokens reais, sem gravá-los."""
import argparse
import base64
import getpass
import json
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('config', help='deployment.local.json')
args = parser.parse_args()
config = json.load(open(args.config))
app_url = 'https://' + config['APP_HOST']
auth_url = 'https://' + config['AUTH_HOST']


def request(url, expected=200, token=None):
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as response:
            status, body, final_url = response.status, response.read(), response.url
    except urllib.error.HTTPError as error:
        status, body, final_url = error.code, b'', error.url
    if status != expected or urlparse(final_url).scheme != 'https':
        raise SystemExit(f'Falha: {url} retornou {status}; esperado {expected}, via HTTPS')
    print(f'OK {expected}: {url}')
    return body


def claims(token):
    # A assinatura é validada pelas APIs. Aqui só verificamos a identidade do cenário.
    parts = token.split('.')
    if len(parts) != 3:
        raise SystemExit('Informe um access_token JWT completo')
    try:
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
    except (ValueError, UnicodeError):
        raise SystemExit('JWT inválido')
    if payload.get('iss') != auth_url + '/realms/bibliogest' or payload.get('exp', 0) < time.time() + 30:
        raise SystemExit('Token expirado, prestes a expirar ou de outro realm')
    audience = payload.get('aud', [])
    if isinstance(audience, str): audience = [audience]
    if not {'bibliogest-acervo', 'bibliogest-reservas'} <= set(audience):
        raise SystemExit('O token não contém as audiências das duas APIs')
    return payload


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def verify_http(host):
    try:
        with urllib.request.build_opener(NoRedirect).open('http://' + host + '/', timeout=10) as response:
            raise SystemExit(f'HTTP sem redirecionamento em {host}: {response.status}')
    except urllib.error.HTTPError as error:
        location = urlparse(error.headers.get('Location', ''))
        if error.code not in (301, 302, 303, 307, 308) or location.scheme != 'https' or location.hostname != host:
            raise SystemExit(f'Configure redirecionamento HTTP para HTTPS em {host}')
    except urllib.error.URLError as error:
        # Fechar completamente a porta HTTP também é permitido.
        if not isinstance(error.reason, (ConnectionRefusedError, TimeoutError)):
            raise SystemExit(f'Não foi possível verificar HTTP em {host}')
    print(f'OK: {host} exige HTTPS')


request(app_url + '/')
verify_http(config['APP_HOST'])
verify_http(config['AUTH_HOST'])
runtime = json.loads(request(app_url + '/config.json'))
if runtime['keycloakUrl'] != auth_url:
    raise SystemExit('Frontend aponta para outro Keycloak')
discovery = json.loads(request(auth_url + '/realms/bibliogest/.well-known/openid-configuration'))
if discovery['issuer'] != auth_url + '/realms/bibliogest':
    raise SystemExit('Issuer publicado incorreto')
for app in ('acervo', 'reservas'):
    request(f'{app_url}/{app}/api/status', 401)
    request(f'{app_url}/{app}/api/status', 401, 'invalid-token')

print('Faça login pelo frontend com cada conta e copie o access_token da resposta /token em Network/DevTools.')
reader = getpass.getpass('Access token da conta cadastrada publicamente (LEITOR): ')
librarian = getpass.getpass('Access token da conta promovida pela administração (BIBLIOTECARIO): ')
reader_claims, librarian_claims = claims(reader), claims(librarian)
reader_roles = set(reader_claims.get('realm_access', {}).get('roles', []))
librarian_roles = set(librarian_claims.get('realm_access', {}).get('roles', []))
if 'LEITOR' not in reader_roles or 'BIBLIOTECARIO' in reader_roles:
    raise SystemExit('O cadastro público não recebeu somente o perfil esperado')
if 'BIBLIOTECARIO' not in librarian_roles:
    raise SystemExit('A segunda conta ainda não recebeu BIBLIOTECARIO')
if reader_claims['sub'] == librarian_claims['sub']:
    raise SystemExit('Use duas contas distintas')
# Altera o payload mantendo a assinatura: ambas as APIs precisam rejeitá-lo.
parts = reader.split('.')
forged = dict(reader_claims)
forged['realm_access'] = {'roles': ['BIBLIOTECARIO']}
parts[1] = base64.urlsafe_b64encode(json.dumps(forged).encode()).decode().rstrip('=')
for app in ('acervo', 'reservas'):
    base = f'{app_url}/{app}/api'
    request(base + '/status', 200, reader)
    request(base + '/admin/status', 403, reader)
    request(base + '/status', 200, librarian)
    request(base + '/admin/status', 200, librarian)
    request(base + '/admin/status', 401, '.'.join(parts))
print('HTTPS, tokens assinados, LEITOR e BIBLIOTECARIO validados nas duas APIs.')
