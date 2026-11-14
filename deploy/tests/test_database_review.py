import base64
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import yaml

BASE = Path(__file__).resolve().parents[1]
ROOT = BASE.parents[2]
spec = importlib.util.spec_from_file_location('verify_api_databases', BASE/'scripts/verify-api-databases.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def pod_spec(app):
    return {'containers':[{'name':app,'env':[
        {'name':'DB_URL','value':f'jdbc:postgresql://postgres:5432/bibliogest_{app}'},
        {'name':'DB_USERNAME','valueFrom':{'secretKeyRef':{'name':f'{app}-db','key':'username'}}},
        {'name':'DB_PASSWORD','valueFrom':{'secretKeyRef':{'name':f'{app}-db','key':'password'}}}]}]}


class DatabaseReviewTests(unittest.TestCase):
    def test_readonly_check_requires_ready_pod_and_up_health(self):
        for app in ('acervo','reservas'):
            deployment={'spec':{'template':{'spec':pod_spec(app)},'selector':{'matchLabels':{'app':app}}}}
            pods={'items':[{'metadata':{'name':app+'-1'},'spec':pod_spec(app),
                            'status':{'phase':'Running','conditions':[{'type':'Ready','status':'True'}]}}]}
            output=io.StringIO()
            with patch.object(checker,'kubectl',side_effect=[deployment,pods,{'status':'UP'}]) as kube, contextlib.redirect_stdout(output):
                checker.verify_api('fixture',app)
            self.assertIn(f'postgres:5432/bibliogest_{app}',output.getvalue())
            arguments=[call.args[1] for call in kube.call_args_list]
            self.assertTrue(all(args[0] in {'get','exec'} for args in arguments))
            self.assertIn(f'http://127.0.0.1:8080/{app}/actuator/health/readiness',arguments[-1])

    def test_live_pod_pointing_at_other_database_is_rejected_before_probe(self):
        deployment={'spec':{'template':{'spec':pod_spec('acervo')},'selector':{'matchLabels':{'app':'acervo'}}}}
        wrong=pod_spec('acervo')
        wrong['containers'][0]['env'][0]['value']='jdbc:postgresql://postgres:5432/bibliogest_reservas'
        pods={'items':[{'metadata':{'name':'acervo-1'},'spec':wrong,
                        'status':{'phase':'Running','conditions':[{'type':'Ready','status':'True'}]}}]}
        with patch.object(checker,'kubectl',side_effect=[deployment,pods]) as kube:
            with self.assertRaisesRegex(ValueError,'DB_URL'):
                checker.verify_api('fixture','acervo')
            self.assertEqual(2,kube.call_count)

    def test_down_health_is_rejected(self):
        deployment={'spec':{'template':{'spec':pod_spec('acervo')},'selector':{'matchLabels':{'app':'acervo'}}}}
        pods={'items':[{'metadata':{'name':'acervo-1'},'spec':pod_spec('acervo'),
                        'status':{'phase':'Running','conditions':[{'type':'Ready','status':'True'}]}}]}
        with patch.object(checker,'kubectl',side_effect=[deployment,pods,{'status':'DOWN'}]):
            with self.assertRaisesRegex(ValueError,'UP'):
                checker.verify_api('fixture','acervo')

    def test_secret_validation_does_not_print_values(self):
        data={key:base64.b64encode(b'private-test-value').decode() for key in ('username','password')}
        output=io.StringIO()
        with patch.object(checker,'kubectl',return_value={'data':data}), contextlib.redirect_stdout(output):
            checker.verify_secret('fixture','acervo-db')
        self.assertEqual('',output.getvalue())
        with patch.object(checker,'kubectl',return_value={'data':{'username':data['username']}}):
            with self.assertRaisesRegex(ValueError,'password'):
                checker.verify_secret('fixture','acervo-db')

    def test_git_ignores_credentials_but_preserves_migrations_examples_and_manifests(self):
        denied=['.env','.env.production','nested/.env.local','deployment.local.json','nested/deployment.local.json',
                'server.key','nested/private.pem','local.kubeconfig','.kube/config','kubeconfig.local.yaml',
                'database.dump','backup.sql','backup.sql.gz','nested/backup.dump.gz',
                'src/main/resources/application-local.yml']
        allowed=['pom.xml','Dockerfile','.env.example','.env.production.example','deployment.example.json',
                 'src/main/resources/application.yaml','src/main/resources/db/migration/V1__schema.sql',
                 'src/main/resources/db/migration/postgresql/V2__schema.sql','deploy/k8s/30-acervo.yaml']
        for relative in ('microsservices/acervo-api','microsservices/reservas-api','front/bibliogest-frontend'):
            repo=ROOT/relative
            with self.subTest(repository=relative):
                result=subprocess.run(['git','-C',str(repo),'check-ignore','--no-index','--stdin'],
                                      input='\n'.join(denied+allowed)+'\n',text=True,capture_output=True)
                self.assertIn(result.returncode,(0,1),result.stderr)
                self.assertEqual(set(denied),set(result.stdout.splitlines()))
                dockerignore=(repo/'.dockerignore').read_text()
                for rule in ('**/.env','**/deployment.local.json','**/*.key','**/*.pem','**/kubeconfig*','**/*.dump',
                             '!**/db/migration/**/*.sql'):
                    # deployment.local.json e kubeconfig* são cobertos por padrões mais gerais/equivalentes.
                    if rule=='**/deployment.local.json':rule='**/*.local.json'
                    if rule=='**/kubeconfig*':rule='**/kubeconfig.*'
                    self.assertIn(rule,dockerignore)

    def test_jdbc_flyway_security_and_health_configuration_are_preserved(self):
        namespace={'m':'http://maven.apache.org/POM/4.0.0'}
        for app in ('acervo','reservas'):
            repo=ROOT/'microsservices'/f'{app}-api'
            pom=ET.parse(repo/'pom.xml')
            dependencies={dep.findtext('m:artifactId',namespaces=namespace):dep for dep in pom.findall('m:dependencies/m:dependency',namespace)}
            self.assertTrue({'spring-boot-starter-data-jpa','spring-boot-starter-flyway','postgresql','flyway-database-postgresql',
                             'spring-boot-starter-security-oauth2-resource-server'} <= dependencies.keys())
            self.assertEqual('runtime',dependencies['postgresql'].findtext('m:scope',namespaces=namespace))
            config=next(yaml.safe_load_all((repo/'src/main/resources/application.yaml').read_text()))
            self.assertEqual(f'${{DB_URL:jdbc:postgresql://postgres:5432/bibliogest_{app}}}',config['spring']['datasource']['url'])
            self.assertEqual('${DB_USERNAME}',config['spring']['datasource']['username'])
            self.assertEqual('${DB_PASSWORD}',config['spring']['datasource']['password'])
            self.assertEqual('validate',config['spring']['jpa']['hibernate']['ddl-auto'])
            jwt=config['spring']['security']['oauth2']['resourceserver']['jwt']
            self.assertEqual(f'bibliogest-{app}',jwt['audiences'])
            self.assertEqual('${OIDC_ISSUER_URI}',jwt['issuer-uri'])
            self.assertIn('db',config['management']['endpoint']['health']['group']['readiness']['include'].split(','))


if __name__=='__main__':unittest.main()
