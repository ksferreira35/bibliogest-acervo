#!/usr/bin/env python3
"""Valida YAML contra os esquemas OpenAPI existentes no cache local do kubectl."""
from pathlib import Path
import argparse
import json,yaml
from jsonschema import Draft4Validator
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('manifest', type=Path)
parser.add_argument('--schema-cache', type=Path, default=Path.home()/'.kube/cache/http')
args=parser.parse_args()
if not args.schema_cache.is_dir():
    parser.exit(1, 'Cache OpenAPI ausente; use kubectl apply --dry-run=server em uma sessão com acesso ao cluster.\n')
schemas={}
for p in args.schema_cache.iterdir():
    if not p.is_file(): continue
    b=p.read_bytes()
    for marker in (b'"swagger"',b'"openapi"'):
        i=b.find(marker)
        if i<0: continue
        start=b.rfind(b'{',0,i)
        try: doc,_=json.JSONDecoder().raw_decode(b[start:].decode())
        except (ValueError,UnicodeError): continue
        entries=doc.get('components',{}).get('schemas',doc.get('definitions',{}))
        for key,value in entries.items():
            value=json.loads(json.dumps(value).replace('#/components/schemas/','#/definitions/'))
            schemas[key]=value
index={}
for key,schema in schemas.items():
    for gvk in schema.get('x-kubernetes-group-version-kind',[]):
        index[(gvk['group']+'/' if gvk['group'] else '')+gvk['version'],gvk['kind']]=key
errors=0
for doc in yaml.safe_load_all(args.manifest.read_text()):
    gvk=doc['apiVersion'],doc['kind']
    if gvk not in index:
        print('Esquema ausente:',gvk);errors+=1;continue
    validator=Draft4Validator({'$ref':'#/definitions/'+index[gvk],'definitions':schemas})
    found=list(validator.iter_errors(doc))
    for error in found:
        print(doc['kind'],doc['metadata']['name'],list(error.path),error.message);errors+=1
    if not found:print('OK:',doc['kind'],doc['metadata']['name'])
print(f'{len(schemas)} esquemas locais carregados; {errors} erros')
raise SystemExit(bool(errors))
