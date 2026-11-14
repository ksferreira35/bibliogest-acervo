"""Exercita o fluxo dos scripts com ferramentas falsas, sem acessar o cluster."""
import base64
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import yaml

BASE = Path(__file__).resolve().parents[1]

MOCK = r'''#!/usr/bin/env python3
import base64,json,os,sys
from pathlib import Path
args=sys.argv[1:]
with open(os.environ['COMMAND_LOG'],'a') as stream:
    stream.write(json.dumps({'tool':Path(sys.argv[0]).name,'args':args})+'\n')
if '-f' in args and args[args.index('-f')+1]=='-':sys.stdin.read()
if Path(sys.argv[0]).name=='openssl':
    sys.stdin.read()
    print('certificate-fixture')
    sys.exit(0)
if 'rollout' in args and os.environ.get('FAIL_RESOURCE') in args:sys.exit(1)
if 'current-context' in args:print('fixture-context')
elif 'nodes' in args and '-o' in args and 'json' in args:
    print(json.dumps({'items':[{}]*int(os.environ.get('NODE_COUNT','1'))}))
elif 'pods' in args and 'get' in args:print(json.dumps({'items':[{'spec':{'hostNetwork':False}}]}))
elif 'secrets' in args and 'get' in args:
    items=[]
    for name in ('postgres-admin','keycloak-admin','pgadmin-admin','acervo-db','reservas-db','keycloak-db'):
        data={'password':base64.b64encode(b'test-only-placeholder').decode()}
        if name.endswith('-db'):data['username']=base64.b64encode(('bibliogest_'+name[:-3]).encode()).decode()
        if name=='keycloak-admin':data['username']=base64.b64encode(b'bootstrap-admin').decode()
        items.append({'metadata':{'name':name},'data':data})
    print(json.dumps({'items':items}))
elif 'pvc' in args and 'get' in args:print('{"items":[]}')
elif 'secret' in args and 'get' in args:
    if 'acervo-db' in args or 'reservas-db' in args:
        print(json.dumps({'data':{'username':base64.b64encode(b'test-user').decode(),'password':base64.b64encode(b'test-password').decode()}}))
        sys.exit(0)
    certificate=base64.b64encode(b'test-only-certificate').decode()
    if '-o' in args and args[args.index('-o')+1]=='json':print(json.dumps({'type':'kubernetes.io/tls','data':{'tls.crt':certificate}}))
    else:print(certificate)
elif 'configmap' in args and 'create' in args:print('{"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"fixture"}}')
elif Path(sys.argv[0]).name=='engine' and 'save' in args:Path(args[args.index('--output')+1]).write_text('image-fixture')
else:print('ok')
'''


class ScriptTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.log = self.root / 'commands.jsonl'
        for name in ('kubectl', 'openssl', 'engine', 'ctr'):
            path = self.root / name
            path.write_text(MOCK)
            path.chmod(0o755)
        self.config = self.root / 'config.json'
        config = json.loads((BASE / 'deployment.example.json').read_text())
        config.update(APP_HOST='app.example.net', AUTH_HOST='login.example.net')
        self.config.write_text(json.dumps(config))
        self.env = dict(os.environ, COMMAND_LOG=str(self.log), KUBECTL_BIN=str(self.root / 'kubectl'),
                        BIBLIOGEST_RENDER_DIR=str(self.root / 'rendered'),
                        CONTAINER_ENGINE=str(self.root / 'engine'), RKE2_CTR_BIN=str(self.root / 'ctr'))
        self.env['PATH'] = str(self.root) + os.pathsep + self.env['PATH']

    def run_script(self, script, *args):
        return subprocess.run([str(BASE / 'scripts' / script), *map(str, args)], env=self.env,
                              capture_output=True, text=True, timeout=30)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def applied(self):
        return [Path(args[index+1]).name for command in self.commands()
                if 'apply' in (args:=command['args']) and '--dry-run=server' not in args
                for index,value in enumerate(args[:-1]) if value=='-f']

    def test_complete_deploy_orders_dependencies(self):
        result = self.run_script('deploy.sh', 'fixture-context', self.config)
        self.assertEqual(0, result.returncode, result.stdout+result.stderr)
        order = self.applied()
        names = ['10-postgres.yaml','11-databases.yaml','20-keycloak.yaml','30-acervo.yaml',
                 '31-reservas.yaml','40-frontend.yaml','45-pgadmin.yaml','50-ingress.yaml']
        self.assertEqual(sorted(order.index(name) for name in names), [order.index(name) for name in names])
        self.assertNotIn('test-only-placeholder', result.stdout+result.stderr)
        self.assertFalse(any('create' in c['args'] and 'secrets' in c['args'] for c in self.commands()))

    def test_pgadmin_can_deploy_without_public_hosts_or_tls(self):
        self.config.write_text((BASE / 'deployment.example.json').read_text())
        result = self.run_script('deploy.sh','fixture-context',self.config,'pgadmin')
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        self.assertIn('45-pgadmin.yaml',self.applied())
        self.assertNotIn('20-keycloak.yaml',self.applied())
        self.assertNotIn('50-ingress.yaml',self.applied())
        self.assertFalse(any('bibliogest-tls' in command['args'] for command in self.commands()))

    def test_failed_dependency_stops_before_api_publication(self):
        self.env['FAIL_RESOURCE'] = 'deployment/keycloak'
        result = self.run_script('deploy.sh','fixture-context',self.config,'all')
        self.assertNotEqual(0,result.returncode)
        self.assertNotIn('30-acervo.yaml',self.applied())
        self.assertNotIn('50-ingress.yaml',self.applied())

    def test_single_component_does_not_apply_other_applications(self):
        result = self.run_script('deploy.sh','fixture-context',self.config,'acervo')
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        self.assertIn('30-acervo.yaml',self.applied())
        self.assertNotIn('31-reservas.yaml',self.applied())
        self.assertNotIn('40-frontend.yaml',self.applied())
        self.assertFalse(any('create' in c['args'] and '-f' in c['args'] and '-' in c['args'] for c in self.commands()))
        self.assertFalse(any('job/database-init' in c['args'] for c in self.commands()))

    def test_build_imports_into_rke2_without_registry_push(self):
        result = self.run_script('build-images.sh','fixture-context',self.config,'acervo','--import')
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        builds = [c['args'] for c in self.commands() if c['tool']=='engine' and 'build' in c['args']]
        self.assertEqual(1,len(builds))
        self.assertEqual(BASE.parent,Path(builds[0][-1]))
        self.assertTrue(any(c['tool']=='ctr' and 'k8s.io' in c['args'] and 'import' in c['args'] for c in self.commands()))
        self.assertFalse(any('push' in c['args'] for c in self.commands()))

    def test_rke2_entrypoint_uses_selected_kubeconfig_and_context(self):
        kubeconfig=self.root/'rke2.yaml'
        kubeconfig.write_text('test-only-kubeconfig')
        self.env['KUBECONFIG']=str(kubeconfig)
        self.env['RKE2_CONTEXT']='expected-rke2-context'
        result=self.run_script('rke2.sh','deploy',self.config,'pgadmin')
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        commands=[c for c in self.commands() if c['tool']=='kubectl']
        self.assertTrue(commands)
        self.assertTrue(all('--context' in c['args'] and c['args'][c['args'].index('--context')+1]=='expected-rke2-context' for c in commands))

    def test_local_import_rejects_multiple_nodes_before_build(self):
        self.env['NODE_COUNT']='2'
        result = self.run_script('build-images.sh','fixture-context',self.config,'all','--import')
        self.assertNotEqual(0,result.returncode)
        self.assertFalse(any(c['tool']=='engine' for c in self.commands()))

    def test_pgadmin_update_also_updates_its_init_container(self):
        result = self.run_script('update.sh','fixture-context','pgadmin','dpage/pgadmin4:9.19')
        self.assertEqual(0,result.returncode,result.stdout+result.stderr)
        updates=[c['args'] for c in self.commands() if 'set' in c['args'] and 'image' in c['args']]
        self.assertTrue(updates)
        self.assertTrue(all('prepare-database-passwords=dpage/pgadmin4:9.19' in args for args in updates))

    def test_pgpass_is_private_and_refreshed_from_application_secrets(self):
        document = next(doc for doc in yaml.safe_load_all((BASE/'k8s/45-pgadmin.yaml').read_text()) if doc['kind']=='ConfigMap')
        code=document['data']['prepare-pgpass.py']
        runtime=self.root/'runtime'
        credentials=self.root/'credentials'
        for app in ('acervo','reservas','keycloak'):
            directory=credentials/app
            directory.mkdir(parents=True)
            (directory/'username').write_text('bibliogest_'+app)
            (directory/'password').write_text('private:pass\\word')
        code=code.replace('/var/lib/pgadmin/storage',str(runtime)).replace('/credentials',str(credentials))
        with patch.dict(os.environ,{'PGADMIN_DEFAULT_EMAIL':'admin@example.com'}):
            exec(compile(code,'pgpass.py','exec'),{})
        path=runtime/'admin_example.com/.pgpass'
        self.assertEqual(0o600,path.stat().st_mode & 0o777)
        self.assertEqual(3,len(path.read_text().splitlines()))
        self.assertIn(r'private\:pass\\word',path.read_text())

    def test_lost_pgadmin_secret_with_existing_volume_is_not_recreated(self):
        config_secrets={'items':[]}
        for name in ('postgres-admin','keycloak-admin','acervo-db','reservas-db','keycloak-db'):
            config_secrets['items'].append({'metadata':{'name':name},'data':{'password':'dGVzdA=='}})
        calls=[json.dumps(config_secrets).encode(),json.dumps({'items':[{'metadata':{'name':'pgadmin-data'}}]}).encode()]
        with patch('sys.argv',['create-secrets.py','fixture']), patch('subprocess.check_output',side_effect=calls), patch('subprocess.run') as create, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit):
                runpy.run_path(str(BASE/'scripts/create-secrets.py'),run_name='__main__')
            create.assert_not_called()

    def test_first_install_creates_secrets_when_kubectl_returns_empty_output(self):
        for output in (b'', b'\n  '):
            with self.subTest(output=output):
                stdout=io.StringIO()
                with patch('sys.argv',['create-secrets.py','fixture']), \
                     patch('subprocess.check_output',side_effect=[output,b'{"items":[]}']), \
                     patch('subprocess.run',return_value=subprocess.CompletedProcess([],0)) as create, \
                     patch('secrets.token_urlsafe',side_effect=[f'generated-password-{index}' for index in range(6)]), \
                     contextlib.redirect_stdout(stdout):
                    runpy.run_path(str(BASE/'scripts/create-secrets.py'),run_name='__main__')
                resources=[json.loads(call.kwargs['input']) for call in create.call_args_list]
                self.assertEqual({'postgres-admin','keycloak-admin','pgadmin-admin','acervo-db','reservas-db','keycloak-db'},
                                 {resource['metadata']['name'] for resource in resources})
                self.assertEqual(6,len({resource['stringData']['password'] for resource in resources}))
                self.assertNotIn('generated-password',stdout.getvalue())

    def test_single_existing_secret_is_preserved(self):
        existing={'apiVersion':'v1','kind':'Secret','metadata':{'name':'postgres-admin'},'data':{'password':'dGVzdA=='}}
        with patch('sys.argv',['create-secrets.py','fixture']), \
             patch('subprocess.check_output',side_effect=[json.dumps(existing).encode(),b'{"items":[]}']), \
             patch('subprocess.run',return_value=subprocess.CompletedProcess([],0)) as create, \
             contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(BASE/'scripts/create-secrets.py'),run_name='__main__')
        names={json.loads(call.kwargs['input'])['metadata']['name'] for call in create.call_args_list}
        self.assertEqual(5,len(names))
        self.assertNotIn('postgres-admin',names)

    def test_missing_secrets_with_existing_postgres_volume_stops_before_creation(self):
        pvcs={'items':[{'metadata':{'name':'data-postgres-0'}}]}
        with patch('sys.argv',['create-secrets.py','fixture']), \
             patch('subprocess.check_output',side_effect=[b'',json.dumps(pvcs).encode()]), \
             patch('subprocess.run') as create:
            with self.assertRaisesRegex(SystemExit,'PVC existente sem credenciais originais'):
                runpy.run_path(str(BASE/'scripts/create-secrets.py'),run_name='__main__')
            create.assert_not_called()

    def test_unreadable_volume_response_stops_before_creating_secrets(self):
        for output in (b'',b'not-json'):
            with self.subTest(output=output), \
                 patch('sys.argv',['create-secrets.py','fixture']), \
                 patch('subprocess.check_output',side_effect=[b'',output]), \
                 patch('subprocess.run') as create:
                with self.assertRaisesRegex(SystemExit,'Consulta de pvc retornou JSON inválido ou vazio'):
                    runpy.run_path(str(BASE/'scripts/create-secrets.py'),run_name='__main__')
                create.assert_not_called()


if __name__=='__main__':unittest.main()
