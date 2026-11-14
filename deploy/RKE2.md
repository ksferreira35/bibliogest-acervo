# Executar no servidor RKE2

O ponto de entrada é `scripts/rke2.sh`. Ele usa o kubeconfig `/etc/rancher/rke2/rke2.yaml` e o kubectl `/var/lib/rancher/rke2/bin/kubectl`. Não instala nem reconfigura o RKE2. Todos os comandos abaixo partem da pasta `microsservices/acervo-api/deploy`.

O script funciona com Bash, Python 3 e OpenSSL. Para construir imagens também precisa de Docker ou Podman e acesso à internet para baixar bases e dependências. Java, Maven e Node são usados dentro dos builds, sem exigir instalação deles no host. A implantação pode ser executada separadamente se as imagens já estiverem disponíveis. Não é necessário instalar PyYAML no servidor para implantar; essa dependência é apenas dos testes offline.

## 1. Conferir e configurar

```bash
cd /home/kaiky/bibliogest-org/microsservices/acervo-api/deploy
sudo ./scripts/rke2.sh inspect
cp deployment.example.json deployment.local.json
nano deployment.local.json
```

Preencha os domínios da aplicação/login e as três imagens versionadas. Ajuste `STORAGE_CLASS` à classe exibida em `inspect`: RKE2 não garante que exista `local-path`. Confira classe, namespace e labels do Ingress. A configuração local lida nesta preparação especifica Traefik; os pods/classes do cluster ainda precisam ser conferidos por você com acesso ao servidor. `INGRESS_CONTROLLER` aceita `traefik` ou `nginx` e seleciona as anotações correspondentes. Não há instalação/troca automática do controller existente.

`PGADMIN_EMAIL` define o usuário inicial de login no pgAdmin (use e-mail em letras minúsculas); `PGADMIN_STORAGE` define o volume, por padrão 1 GiB. `PGADMIN_IMAGE` está fixada em `dpage/pgadmin4:9.19`. Configurações antigas recebem esses novos parâmetros por padrão, sem reescrever o arquivo original.

Use uma tag nova a cada atualização das APIs/frontend. Não reconstrua versões diferentes com a mesma tag: `IfNotPresent` e o rollout dependem da mudança da referência da imagem.

## 2. Subir PostgreSQL e pgAdmin agora, mesmo sem domínios/TLS

Com a StorageClass correta, os três comandos abaixo já sobem a parte de bancos. Eles não constroem as APIs nem exigem os domínios definitivos.

```bash
sudo ./scripts/rke2.sh deploy deployment.local.json postgres
sudo ./scripts/rke2.sh deploy deployment.local.json databases
sudo ./scripts/rke2.sh deploy deployment.local.json pgadmin
```

Cada comando valida os manifests no API server, preserva os Secrets existentes e espera os recursos necessários ficarem prontos. Em caso de falha, interrompe. O script cria o namespace quando necessário. Não exclui PVCs ou troca senhas já existentes.

O PostgreSQL continua com **uma instância** e os três bancos. O pgAdmin usa SQLite no próprio PVC somente para configurações/sessões; não cria uma segunda instância PostgreSQL.

## 3. Abrir o pgAdmin no navegador

No servidor, deixe este comando em execução:

```bash
sudo ./scripts/rke2.sh pgadmin
```

Em outro terminal do seu computador, abra o túnel SSH:

```bash
ssh -N -L 5050:127.0.0.1:5050 kaiky@IP_DO_SERVIDOR
```

Abra **http://localhost:5050** no navegador do seu computador. O trecho até o servidor é transportado pelo túnel SSH. O port-forward escuta somente em `127.0.0.1`. Não é necessário abrir 5050 ou 5432 no firewall.

Consulte o login inicial em outro terminal privado no servidor:

```bash
sudo ./scripts/rke2.sh credentials deployment.local.json pgadmin
```

Esse comando exibe a senha apenas em terminal interativo e recusa pipes/redirecionamento para arquivos. A implantação não imprime senhas. A conta do pgAdmin é independente do Keycloak e não tem cadastro público.

Os três bancos aparecem no grupo **BiblioGest**, com host interno `postgres:5432`, banco de manutenção e usuário específicos. As conexões já usam as senhas dos Secrets, sem precisar digitá-las no pgAdmin. Um init container gera o `.pgpass` privado (modo 0600) no diretório do usuário a cada início do pod; as senhas não entram nos manifests ou no Git. O pgAdmin recebe as três credenciais de aplicação, **não** o Secret do superusuário. Cada conexão continua restrita ao próprio banco pelo PostgreSQL.

O volume de 1 GiB preserva contas, conexões, preferências e sessões. Trate backups desse volume como sensíveis porque ele contém o `.pgpass`. Não altere `PGADMIN_EMAIL` depois da inicialização esperando renomear a conta existente: gerencie usuários pelo pgAdmin. Alterar a senha no Secret também não altera uma conta já criada no SQLite; use a interface e mantenha sua credencial atual em um gerenciador de senhas. `credentials` mostra a credencial inicial armazenada no Secret.

Depois de uma rotação das senhas de banco, atualize PostgreSQL e Secrets e reinicie `deployment/pgadmin` para regenerar o `.pgpass`. As definições de servidores são importadas no primeiro início; mudanças posteriores da lista devem ser aplicadas pela interface de importação do pgAdmin para preservar as conexões que você adicionou. Para recuperar uma instalação com PVC existente, restaure os Secrets correspondentes; o script não inventa uma nova senha para um pgAdmin já inicializado.

## 4. Implantar a pilha inteira

Para publicar frontend, login e APIs, primeiro configure DNS e um certificado válido para **os dois domínios**. Use os arquivos PEM existentes ou obtenha o Secret `bibliogest-tls` pelo cert-manager já instalado no cluster.

```bash
sudo ./scripts/rke2.sh tls /caminho/fullchain.pem /caminho/privkey.pem
```

O comando cria/atualiza somente o Secret TLS do BiblioGest, sem gravar a chave no Git ou em annotation de client-side apply.

Se as imagens já estiverem publicadas no registry e as referências estiverem no JSON:

```bash
sudo ./scripts/rke2.sh deploy deployment.local.json
```

Para **construir as três aplicações e implantar automaticamente em um RKE2 de um único nó**, sem registry:

```bash
sudo ./scripts/rke2.sh up deployment.local.json --import
```

Essa opção detecta Docker ou Podman, executa os três Dockerfiles (incluindo seus testes/builds), salva as imagens em arquivos temporários, importa no namespace `k8s.io` do containerd do RKE2 e remove os arquivos temporários. Depois executa a implantação completa. Os nomes das imagens continuam sendo os do JSON; não é necessário que os repositórios existam no registry para essa opção. As imagens de PostgreSQL, Keycloak e pgAdmin são baixadas normalmente pelo cluster.

A importação local é recusada se houver mais de um nó, pois não distribuiria a imagem aos outros nós. Nesse caso, autentique Docker/Podman no registry com o mesmo usuário que executará os builds e use:

```bash
sudo ./scripts/rke2.sh up deployment.local.json --push
```

Para registry privado, configure também o imagePullSecret do cluster conforme o [guia geral](README.md). Login no Docker/Podman não fornece automaticamente credenciais de pull ao containerd do RKE2.

A sequência completa é namespace → validações/TLS → Secrets → PostgreSQL → criação dos bancos → Keycloak → Acervo → Reservas → frontend → pgAdmin → Ingress. Os comandos aguardam prontidão; uma falha interrompe as próximas etapas. DNS, emissão de certificado e instalação de provisionador de armazenamento não podem ser deduzidos pelo script e devem estar configurados. Certificado ausente ou classe inexistente produz uma mensagem/erro antes de subir os workloads da publicação completa.

## 5. Implantar ou atualizar cada componente

Estes comandos aplicam somente o workload escolhido e a configuração compartilhada. Verificam as dependências existentes; não reaplicam as outras aplicações.

```bash
sudo ./scripts/rke2.sh deploy deployment.local.json keycloak
sudo ./scripts/rke2.sh deploy deployment.local.json acervo
sudo ./scripts/rke2.sh deploy deployment.local.json reservas
sudo ./scripts/rke2.sh deploy deployment.local.json frontend
sudo ./scripts/rke2.sh deploy deployment.local.json pgadmin
sudo ./scripts/rke2.sh deploy deployment.local.json ingress
```

Exemplo de atualização de apenas Reservas sem registry: coloque uma **nova tag** em `RESERVAS_IMAGE`, construa/importe somente Reservas e aplique seu Deployment:

```bash
sudo ./scripts/rke2.sh images deployment.local.json reservas --import
sudo ./scripts/rke2.sh deploy deployment.local.json reservas
```

Se a nova imagem já estiver disponível, você também pode alterar só sua referência:

```bash
sudo ./scripts/rke2.sh update acervo ghcr.io/ksferreira35/bibliogest-acervo:NOVA_TAG
sudo ./scripts/rke2.sh update reservas ghcr.io/ksferreira35/bibliogest-reservas:NOVA_TAG
sudo ./scripts/rke2.sh update frontend ghcr.io/ksferreira35/bibliogest-frontend:NOVA_TAG
sudo ./scripts/rke2.sh update pgadmin dpage/pgadmin4:NOVA_VERSAO
```

O update do pgAdmin altera também a imagem de seu init container. Registre a nova versão no JSON para não revertê-la em um deploy completo futuro. Faça backup e confira migrações antes de atualizar Keycloak, PostgreSQL ou pgAdmin. O PostgreSQL é atualizado pelo manifest do StatefulSet, não pelo comando `update` de aplicações.

Se usar paths ou kubeconfig personalizados, informe `KUBECONFIG`, `KUBECTL_BIN` e, se necessário, `RKE2_CONTEXT`. A importação aceita `RKE2_CTR_BIN` e `RKE2_CONTAINERD_ADDRESS`. Para escolher o builder, informe `CONTAINER_ENGINE=docker` ou `podman`. Preserve explicitamente essas variáveis ao usar sudo. Execute os comandos de implantação em sequência, sem duas execuções simultâneas usando o mesmo diretório renderizado.

## Verificação das APIs e dos Secrets existentes

Após o rollout, confira os dois Secrets e a readiness JDBC dos pods, sem imprimir senhas nem alterar dados:

```bash
sudo ./scripts/rke2.sh verify-apis
```

Para verificar apenas uma API, acrescente `acervo` ou `reservas`. O deploy individual dessas APIs usa somente seus Secrets existentes e não exige o Job histórico `database-init`. Consulte [POSTGRESQL.md](POSTGRESQL.md) para o diagnóstico, auditoria de arquivos rastreados e limites das validações realizadas.

## Verificação

Nesta preparação, os manifests foram renderizados e validados offline contra os esquemas OpenAPI locais. Os fluxos de ordem, falha, isolamento de updates, importação local e proteção de senhas foram exercitados com kubectl/builder simulados, sem acesso ao servidor. Builds reais, pulls de imagens e rollouts continuam dependendo da execução no RKE2.

Depois de implantar, use os testes de bancos e perfis do [guia geral](README.md). Se o controller usar `hostNetwork`, adapte e valide as NetworkPolicies para a rede dos nós antes de publicar: o script para nesse caso em vez de liberar tráfego de origens não identificadas.

Referências: [acesso ao RKE2](https://docs.rke2.io/cluster_access), [containerd do RKE2](https://docs.rke2.io/reference/cli_tools), [pgAdmin em containers](https://www.pgadmin.org/docs/pgadmin4/9.19/container_deployment.html).
