#!/usr/bin/env python3
"""Audita arquivos rastreados; imprime somente caminhos e tipos de achado."""
import fnmatch
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[4]
REPOSITORIES = (ROOT/'microsservices/acervo-api', ROOT/'microsservices/reservas-api', ROOT/'front/bibliogest-frontend')
PATTERNS = ('.env', '.env.*', '*.local.json', '*.local.yaml', '*.local.yml', '*.local.properties', 'application-local.*',
            '*.pem', '*.key', '*.p12', '*.pfx', '*.jks', '*.keystore', 'id_rsa', 'id_ed25519', 'id_ecdsa',
            'kubeconfig*', '*.kubeconfig', '.pgpass', '*.dump', '*.dump.*', '*.backup', '*.backup.*',
            '*.sql', '*.sql.gz', '*.sql.bz2', '*.sql.xz', '*.sql.zip')
PRIVATE_KEY = re.compile(rb'^-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----', re.M)
TOKEN = re.compile(rb'\b(?:ghp_[A-Za-z0-9]{25,}|AKIA[A-Z0-9]{16}|github_pat_[A-Za-z0-9_]{30,})\b')
ASSIGNMENT = re.compile(r'^[ 	]*["\']?(?:[A-Z_]*(?:PASSWORD|PASSWD|CLIENT_SECRET|API_KEY|ACCESS_TOKEN)|password|passwd|client-secret|api-key|access-token)["\']?[ 	]*[:=][ 	]*([^\r\n]*?)[ 	]*,?[ 	]*$', re.M)


def inspect(name, content):
    path = Path(name)
    example = any(marker in path.name for marker in ('.example', '.sample', '.template'))
    migration = 'db/migration/' in name and path.suffix == '.sql'
    findings = set()
    if not example and not migration and (any(fnmatch.fnmatch(path.name, pattern) for pattern in PATTERNS)
                                         or any(part in path.parts for part in ('.kube', '.ssh', '.aws', 'kubeconfigs', 'backups', 'dumps'))):
        findings.add('arquivo local/credencial/dump potencialmente sensível rastreado')
    if PRIVATE_KEY.search(content):
        findings.add('chave privada')
    if TOKEN.search(content):
        findings.add('possível token de acesso')
    if path.suffix in {'.yaml', '.yml', '.json', '.properties', '.conf'} or path.name.startswith('.env'):
        for match in ASSIGNMENT.finditer(content.decode('utf-8', errors='replace')):
            value = match[1].strip().rstrip(',').strip().strip('"\'')
            if value and not value.startswith(('$', '<', '#', '!')) and value not in {'null', 'None', '~', '|', '|-', '>'}:
                findings.add('possível credencial literal na configuração')
    return findings


def main():
    count = 0
    for repo in REPOSITORIES:
        files = subprocess.check_output(['git', '-C', str(repo), 'ls-files', '-z']).decode().split('\0')
        findings = []
        for name in filter(None, files):
            # Revê o índice e o arquivo atual; ignore não protege arquivos já rastreados.
            contents = [subprocess.check_output(['git', '-C', str(repo), 'show', ':'+name])]
            current = repo/name
            if current.is_file():
                contents.append(current.read_bytes())
            reasons = set().union(*(inspect(name, content) for content in contents))
            if reasons:
                findings.append({'path': name, 'findings': sorted(reasons)})
        count += len(findings)
        print(json.dumps({'repository': str(repo.relative_to(ROOT)), 'tracked_files': len(list(filter(None, files))),
                          'findings': findings}, ensure_ascii=False))
    if count:
        print('Revise os caminhos indicados. Remova arquivos sensíveis do índice com git rm --cached e rotacione credenciais expostas; ignorar não remove o histórico.', file=sys.stderr)
    return bool(count)


if __name__ == '__main__':
    sys.exit(main())
