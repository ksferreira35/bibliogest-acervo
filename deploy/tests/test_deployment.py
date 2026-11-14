import importlib.util
import json
import re
from pathlib import Path
import subprocess
import tempfile
import unittest
import yaml

BASE = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('render', BASE / 'scripts/render.py')
render = importlib.util.module_from_spec(spec)
spec.loader.exec_module(render)


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.config = render.load_config(BASE / 'deployment.example.json')

    def rendered(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = render.render(self.config, Path(temporary))
            text = subprocess.check_output(['kubectl', 'kustomize', str(output)], text=True)
            self.assertNotIn('@@', text)
            return list(yaml.safe_load_all(text))

    def test_storage_and_private_database(self):
        docs = self.rendered()
        postgres = next(doc for doc in docs if doc['kind'] == 'StatefulSet')
        self.assertEqual(1, postgres['spec']['replicas'])
        self.assertEqual('Retain', postgres['spec']['persistentVolumeClaimRetentionPolicy']['whenDeleted'])
        self.assertEqual('20Gi', postgres['spec']['volumeClaimTemplates'][0]['spec']['resources']['requests']['storage'])
        services = [doc for doc in docs if doc['kind'] == 'Service']
        self.assertTrue(all(doc['spec']['type'] == 'ClusterIP' for doc in services))
        ingress = next(doc for doc in docs if doc['kind'] == 'Ingress')
        backends = [path['backend']['service']['name'] for rule in ingress['spec']['rules'] for path in rule['http']['paths']]
        self.assertNotIn('postgres', backends)
        self.assertEqual({'frontend', 'acervo', 'reservas', 'keycloak'}, set(backends))
        self.assertTrue(ingress['spec']['tls'])

    def test_public_registration_cannot_obtain_librarian(self):
        realm = json.loads((BASE / 'k8s/realm-bibliogest.json').read_text())
        default = next(role for role in realm['roles']['realm'] if role['name'] == realm['defaultRole']['name'])
        self.assertEqual(['LEITOR'], default['composites']['realm'])
        self.assertTrue(realm['registrationAllowed'])
        self.assertNotIn('users', realm)
        client = realm['clients'][0]
        self.assertIn('basic', client['defaultClientScopes'])
        self.assertTrue(client['publicClient'])
        self.assertFalse(client['directAccessGrantsEnabled'])
        self.assertFalse(client['implicitFlowEnabled'])
        self.assertEqual('S256', client['attributes']['pkce.code.challenge.method'])
        self.assertFalse(client['serviceAccountsEnabled'])

    def test_no_credentials_in_manifests(self):
        docs = self.rendered()
        self.assertNotIn('Secret', [doc['kind'] for doc in docs])
        for doc in docs:
            if doc['kind'] not in {'Deployment', 'StatefulSet'}:
                continue
            for container in doc['spec']['template']['spec']['containers']:
                render.pinned_image(container['image'])
                for env in container.get('env', []):
                    if 'PASSWORD' in env['name'] and not env['name'].endswith('_FILE'):
                        self.assertIn('secretKeyRef', env['valueFrom'])

    def test_application_update_is_independent(self):
        original = {doc['metadata']['name']: doc for doc in self.rendered() if doc['kind'] == 'Deployment'}
        self.config['ACERVO_IMAGE'] = 'ghcr.io/example/acervo:0.2.0'
        updated = {doc['metadata']['name']: doc for doc in self.rendered() if doc['kind'] == 'Deployment'}
        self.assertNotEqual(original['acervo'], updated['acervo'])
        for app in ('reservas', 'frontend', 'keycloak', 'pgadmin'):
            self.assertEqual(original[app], updated[app])

    def test_api_environment_matches_deployment_configuration(self):
        docs = self.rendered()
        configmaps = {doc['metadata']['name']: doc['data'] for doc in docs if doc['kind'] == 'ConfigMap'}
        for app in ('acervo', 'reservas'):
            with self.subTest(application=app):
                application = BASE.parent.parent / f'{app}-api/src/main/resources/application.yaml'
                required = set(re.findall(r'\$\{([^}:]+)(?::[^}]*)?\}', application.read_text()))
                deployment = next(doc for doc in docs if doc['kind'] == 'Deployment' and doc['metadata']['name'] == app)
                container = deployment['spec']['template']['spec']['containers'][0]
                supplied = {env['name'] for env in container.get('env', [])}
                for source in container.get('envFrom', []):
                    supplied.update(configmaps[source['configMapRef']['name']])
                self.assertFalse(required - supplied, f'Variáveis não fornecidas no Deployment: {required - supplied}')

    def test_rejects_unversioned_images_and_example_domains(self):
        for image in ('example/acervo', 'example/acervo:latest', 'example/acervo:main', 'example/acervo@sha256:bad'):
            with self.assertRaises(ValueError):
                render.pinned_image(image)
        with self.assertRaises(ValueError):
            render.load_config(BASE / 'deployment.example.json', production=True)

    def test_cross_database_authentication_is_rejected(self):
        docs = self.rendered()
        config = next(doc for doc in docs if doc['kind'] == 'ConfigMap' and doc['metadata']['name'] == 'postgres-hba')
        hba = config['data']['pg_hba.conf']
        for app in ('acervo', 'reservas', 'keycloak'):
            self.assertIn(f'host bibliogest_{app} bibliogest_{app} 0.0.0.0/0 scram-sha-256', hba)
        self.assertTrue(hba.rstrip().endswith('host all all ::/0 reject'))


if __name__ == '__main__':
    unittest.main()
