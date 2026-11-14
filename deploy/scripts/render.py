#!/usr/bin/env python3
"""Renderiza parâmetros públicos. Nunca lê nem gera senhas."""
import argparse
import hashlib
import json
import re
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
PARAMETER = re.compile(r'@@([A-Z_]+)@@')


def pinned_image(value):
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9./_:@+-]+', value):
        raise ValueError('Referência de imagem inválida')
    if '@sha256:' in value:
        if not re.fullmatch(r'.+@sha256:[a-f0-9]{64}', value):
            raise ValueError('Digest de imagem inválido')
    elif not re.fullmatch(r'.+:[A-Za-z0-9_][A-Za-z0-9_.-]*', value) or value.rsplit(':', 1)[1] in {'latest', 'main', 'master', 'dev'}:
        raise ValueError('Use uma imagem com tag de versão imutável ou digest sha256')
    return value


def load_config(path, production=False):
    data = json.loads(Path(path).read_text())
    defaults = json.loads((BASE / 'deployment.example.json').read_text())
    # Compatibilidade com configurações criadas antes da inclusão do pgAdmin.
    for key in ('PGADMIN_IMAGE', 'PGADMIN_EMAIL', 'PGADMIN_STORAGE', 'INGRESS_CONTROLLER'):
        data.setdefault(key, defaults[key])
    expected = set(json.loads((BASE / 'deployment.example.json').read_text()))
    if set(data) != expected:
        raise ValueError(f'Parâmetros faltantes: {expected - set(data)}; desconhecidos: {set(data) - expected}')
    for key, value in data.items():
        if not isinstance(value, str) or not value or '\n' in value:
            raise ValueError(f'{key} precisa ser uma string não vazia')
        if key.endswith('_IMAGE'):
            pinned_image(value)
        elif key == 'PGADMIN_EMAIL':
            if not re.fullmatch(r'[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}', value):
                raise ValueError('PGADMIN_EMAIL deve ser um e-mail em letras minúsculas')
        elif key == 'INGRESS_CONTROLLER':
            if value not in {'traefik', 'nginx'}:
                raise ValueError('INGRESS_CONTROLLER deve ser traefik ou nginx')
        elif key.endswith('_HOST'):
            if len(value) > 253 or not re.fullmatch(r'(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?', value) or '.' not in value:
                raise ValueError(f'Hostname inválido: {key}')
            if any(not label or len(label) > 63 or label.startswith('-') or label.endswith('-') for label in value.split('.')):
                raise ValueError(f'Hostname inválido: {key}')
            if production and value.endswith(('.invalid', '.example', '.test', '.localhost')):
                raise ValueError('Preencha os domínios reais antes de implantar')
        elif not re.fullmatch(r'[a-zA-Z0-9./_-]+', value):
            raise ValueError(f'Valor inválido: {key}')
    if data['APP_HOST'] == data['AUTH_HOST']:
        raise ValueError('Use hostnames distintos para frontend e login')
    for key in ('POSTGRES_STORAGE', 'PGADMIN_STORAGE'):
        if not re.fullmatch(r'[1-9][0-9]*(Gi|Ti)', data[key]):
            raise ValueError(f'{key} deve usar Gi ou Ti')
    return data


def render(config, output):
    output.mkdir(parents=True, exist_ok=True)
    for source in sorted((BASE / 'k8s').iterdir()):
        if source.is_file():
            # Somente mudanças na configuração deste componente reiniciam seus pods.
            keys = {
                '20-keycloak.yaml': ('APP_HOST', 'AUTH_HOST'),
                '30-acervo.yaml': ('AUTH_HOST',),
                '31-reservas.yaml': ('AUTH_HOST',),
                '40-frontend.yaml': ('AUTH_HOST',),
                '45-pgadmin.yaml': ('PGADMIN_EMAIL',),
            }.get(source.name, ())
            checksum_source = json.dumps({key: config[key] for key in keys}, sort_keys=True)
            if source.name == '20-keycloak.yaml':
                checksum_source += (BASE / 'k8s/realm-bibliogest.json').read_text()
            if source.name == '45-pgadmin.yaml':
                checksum_source += (BASE / 'k8s/pgadmin-servers.json').read_text()
            params = dict(config, CONFIG_CHECKSUM=hashlib.sha256(checksum_source.encode()).hexdigest())
            annotations = (
                {'traefik.ingress.kubernetes.io/router.entrypoints': 'websecure',
                 'traefik.ingress.kubernetes.io/router.tls': 'true'}
                if config['INGRESS_CONTROLLER'] == 'traefik' else
                {'nginx.ingress.kubernetes.io/ssl-redirect': 'true',
                 'nginx.ingress.kubernetes.io/force-ssl-redirect': 'true'}
            )
            template = source.read_text().replace("'@@INGRESS_ANNOTATIONS@@'", json.dumps(annotations))
            text = PARAMETER.sub(lambda match: params[match[1]], template)
            (output / source.name).write_text(text)
    (output / 'parameters.json').write_text(json.dumps(config, indent=2) + '\n')
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('config', type=Path)
    parser.add_argument('--output', type=Path, default=BASE / 'rendered')
    parser.add_argument('--production', action='store_true')
    args = parser.parse_args()
    try:
        print(render(load_config(args.config, args.production), args.output))
    except (ValueError, KeyError) as error:
        parser.exit(1, f'{error}\n')
