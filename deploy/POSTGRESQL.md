# APIs e PostgreSQL provisionado

As duas aplicações mantêm seus repositórios, builds, imagens, Deployments, migrations e credenciais independentes. A infraestrutura do PostgreSQL já existe no namespace `bibliogest`.

| Serviço | URL JDBC no cluster | Secret | Chaves |
| --- | --- | --- | --- |
| Acervo | `jdbc:postgresql://postgres:5432/bibliogest_acervo` | `acervo-db` | `username`, `password` |
| Reservas | `jdbc:postgresql://postgres:5432/bibliogest_reservas` | `reservas-db` | `username`, `password` |

Os Deployments `k8s/30-acervo.yaml` e `k8s/31-reservas.yaml` já possuem essas URLs e referências `secretKeyRef`, sem valores de usuário/senha. Foram preservados. `application.yaml` agora também usa a URL interna de seu próprio banco como padrão de `DB_URL`; `DB_USERNAME` e `DB_PASSWORD` continuam obrigatórias. `DB_URL` pode ser sobrescrita explicitamente fora do cluster.

Os comandos individuais `deploy ... acervo` e `deploy ... reservas` conferem apenas o Secret da API escolhida. Não criam/rotacionam Secrets ou bancos e não exigem a existência do Job histórico `database-init`. Continuam aguardando o PostgreSQL e o Keycloak existentes. O deploy completo conserva o fluxo de provisionamento para uma instalação nova.

## Maven, migrations e autenticação

Os dois POMs já incluem JPA, o driver PostgreSQL em runtime, o starter Flyway, o módulo `flyway-database-postgresql`, Actuator e OAuth2 Resource Server. As versões gerenciadas pelo Spring Boot e os plugins de build foram preservados; não foi necessário acrescentar ou duplicar dependências.

Não havia migrations SQL nem entidades de negócio nesta revisão. Foi documentado o diretório `src/main/resources/db/migration` de cada serviço. Não foram inventadas tabelas ou migrations que criem usuários/bancos já provisionados. Futuras alterações reais de schema devem usar migrations próprias de cada aplicação. Permanecem `ddl-auto: validate` e Flyway habilitado; não há baseline automático ou limpeza de schema.

Foram mantidos issuer/JWKS do Keycloak, audience específica de cada API e as permissões `LEITOR` e `BIBLIOTECARIO`. A readiness inclui o indicador JDBC `db`; o endpoint continua interno, sem ser publicado pelo Ingress.

## Build e implantação individual

Build local com JDK 21 e rede, a partir da raiz da organização:

```bash
cd /home/kaiky/bibliogest-org/microsservices/acervo-api
./mvnw -B -ntp verify
cd /home/kaiky/bibliogest-org/microsservices/reservas-api
./mvnw -B -ntp verify
```

Como alternativa, os Dockerfiles já executam `mvn verify` dentro de uma imagem com JDK/Maven. No servidor RKE2 de nó único, coloque tags **novas** em `ACERVO_IMAGE` e `RESERVAS_IMAGE` no `deployment.local.json`, mantenha os domínios reais do login e execute:

```bash
cd /home/kaiky/bibliogest-org/microsservices/acervo-api/deploy
sudo ./scripts/rke2.sh images deployment.local.json acervo --import
sudo ./scripts/rke2.sh deploy deployment.local.json acervo
sudo ./scripts/rke2.sh images deployment.local.json reservas --import
sudo ./scripts/rke2.sh deploy deployment.local.json reservas
```

Não é necessário reaplicar PostgreSQL, criação de bancos, Keycloak ou frontend para essas atualizações. Keycloak e PostgreSQL devem estar disponíveis. Em vários nós, use `--push` com registry e credenciais de pull configuradas, conforme [RKE2.md](RKE2.md).

## Confirmar acesso sem escrever dados

Depois de atualizar os pods, rode:

```bash
cd /home/kaiky/bibliogest-org/microsservices/acervo-api/deploy
sudo ./scripts/rke2.sh verify-apis
# Ou somente uma API:
sudo ./scripts/rke2.sh verify-apis acervo
sudo ./scripts/rke2.sh verify-apis reservas
```

A verificação:

- Consulta os Secrets sem imprimir os valores; exige `username` e `password` presentes e não vazias.
- Confere as URLs e referências dos Secrets no Deployment e nos pods Ready existentes.
- Consulta a readiness dentro de cada pod com `wget`, disponível na imagem Alpine atual. O indicador JDBC deve estar UP.
- Usa apenas consultas de recursos e execução da requisição HTTP interna. Não cria Jobs, usuários ou bancos e não faz INSERT, UPDATE, DELETE, DDL ou migrations para testar.

A readiness comprova a saúde do DataSource da aplicação configurada. Não substitui testes de negócio ou isolamento de todas as permissões SQL. Se não houver pod Ready ou a configuração apontar para outro banco/Secret, o verificador falha com uma mensagem sem exibir credenciais.

## Arquivos locais e Git

Foram reforçados `.gitignore` e `.dockerignore` nos contextos reais de build dos três repositórios. As regras cobrem `.env` reais, `*.local.json` (incluindo `deployment.local.json`), configurações locais do Spring, chaves/keystores, kubeconfigs, diretórios locais de credenciais e dumps SQL/binários/comprimidos, inclusive sob `src/`.

As exceções preservam arquivos de exemplo e SQL em `db/migration`. Manifests que apenas referenciam Secrets continuam permitidos no Git. O diretório `deploy/` fica fora do contexto Docker das APIs; os recursos `src/main/resources`, incluindo migrations, continuam disponíveis ao build. Não há outro Dockerfile/arquivo de ignore específico substituindo os ignores das raízes.

Para repetir a auditoria do índice Git e dos arquivos rastreados atuais:

```bash
python3 scripts/audit-sensitive.py
```

Na revisão atual foram consultados 9 arquivos rastreados do Acervo, 9 de Reservas e 16 do frontend: nenhum achado sensível. O script relata somente caminhos/tipos de achado e não imprime conteúdos. A auditoria não altera o índice. Se houver um arquivo sensível rastreado no futuro, remova-o do índice com `git rm --cached CAMINHO`, preserve a cópia local protegida e rotacione credenciais expostas; adicionar ignore não remove versões já commitadas.

## Validações realizadas

- 27 testes Python/offline passaram: contratos de configuração, dependências XML, Secrets, isolamento dos deploys, regras de ignore, verificação de saúde e demais fluxos existentes.
- 27 recursos renderizados passaram contra 423 esquemas OpenAPI do cache local; manifests foram renderizados em `/tmp`, preservando a configuração local do servidor.
- Sintaxe dos scripts Python/shell e `git diff --check` passaram.
- O build Maven foi tentado nos dois serviços usando cache temporário: ambos pararam no download do Maven por restrição de rede. Não há JDK disponível neste ambiente, e o Podman não pôde acessar seu runtime. O build Java e os testes Java continuam pendentes; não foram declarados como aprovados.
- A consulta dos Secrets no cluster foi tentada sem imprimir valores e falhou com `socket: operation not permitted` em `127.0.0.1:6443`. A existência dos Secrets e o acesso JDBC dos pods não puderam ser confirmados aqui; use `verify-apis` no servidor após o deploy.

Referências utilizadas na revisão: [inicialização/Flyway no Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [contexto e regras de ignore no Docker](https://docs.docker.com/build/concepts/context/).
