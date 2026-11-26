# BiblioGest — Acervo

API para cadastrar e consultar obras, autores, categorias e exemplares físicos de uma biblioteca. Também recebe e disponibiliza as capas das obras.

Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Flyway e Keycloak.

## Desenvolvimento local

Use Java 21, Maven e um banco PostgreSQL próprio para desenvolvimento. Configure um arquivo `.env` na raiz do projeto com os valores do seu ambiente.

```dotenv
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:postgresql://127.0.0.1:15432/bibliogest_acervo
DB_USERNAME=seu_usuario_local
DB_PASSWORD=sua_senha_local
OIDC_ISSUER_URI=https://login.ksferreira.org/realms/bibliogest
OIDC_JWK_SET_URI=https://login.ksferreira.org/realms/bibliogest/protocol/openid-connect/certs
```

No IntelliJ, selecione o `.env` nas variáveis de ambiente da configuração de execução. Pelo terminal, carregue o arquivo antes de iniciar a aplicação.

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

O Flyway aplica as migrations ao iniciar. O Hibernate valida o esquema criado.

- [Swagger local](http://localhost:8080/acervo/swagger-ui/index.html)
- [Saúde da API e do banco](http://localhost:8080/acervo/actuator/health/readiness)
- [BiblioGest publicado](https://bibliogest.ksferreira.org/)

## Rotas

As rotas abaixo usam o prefixo `/acervo/api/v1`.

| Método | Caminho | Operação |
| --- | --- | --- |
| GET / POST | `/categorias` | Listar e cadastrar categorias |
| GET / POST | `/autores` | Listar e cadastrar autores |
| GET / POST | `/obras` | Listar e cadastrar obras |
| GET / PUT / DELETE | `/obras/{id}` | Consultar, editar ou excluir uma obra sem exemplares |
| GET | `/obras/resumo?ids={id1},{id2}` | Consultar até 100 obras por ID em uma chamada |
| GET / POST | `/obras/{obraId}/exemplares` | Listar e cadastrar cópias físicas da obra |
| PUT | `/obras/{obraId}/capa` | Enviar ou substituir a capa |

As consultas exigem um token de leitor ou bibliotecário. Cadastros e envio de capas exigem o perfil `BIBLIOTECARIO`. No Swagger, use **Authorize** com um `access_token` emitido pelo Keycloak para a audiência `bibliogest-acervo`.

O upload recebe um formulário `multipart/form-data` com o campo `arquivo`. Aceita JPEG e PNG de até 5 MB e 2048 × 2048 pixels. A resposta da obra inclui `capaUrl`, que aponta para a imagem pública em `/acervo/capas/{obraId}.jpg`.

Localmente, as capas ficam em `.local/acervo/capas/`. A variável `BIBLIOGEST_CAPAS_DIR` altera o diretório base. No RKE2, o volume `acervo-capas` é montado em `/data`, com os arquivos em `/data/capas/`.

## Testes e build

```bash
mvn verify
```

Os testes usam H2 em memória e tokens simulados. O GitHub Actions executa os testes antes do build.

O Dockerfile gera a imagem da API. Os manifests do homelab ficam em `deploy/k8s/`. A API de Reservas consulta os exemplares por HTTP e mantém a disponibilidade e as reservas no próprio banco.

O catálogo `GET /obras` retorna `{content, pagina, tamanho, totalElementos, totalPaginas}`. Use `pagina` (inicia em 0), `tamanho` (padrão 20, máximo 100), `busca` (título, autor, categoria ou ISBN) e `categoriaId`. A ordenação é por título e ID.

`PUT /obras/{id}` recebe o mesmo corpo do cadastro e substitui título, categoria, autores, volume e ISBN, preservando ID, capa e exemplares. Volume e ISBN omitidos são limpos. Edição e exclusão exigem `BIBLIOTECARIO`; exclusão com exemplares retorna `409` para preservar as referências usadas pelas reservas.

O cadastro e a edição de obras aceitam `descricao`, um texto opcional de até 5.000 caracteres para a sinopse. O campo é retornado nas consultas; valores vazios são armazenados como `null`. No PUT, omitir a descrição ou enviar `null` remove a sinopse anterior.


## Qualidade e cobertura obrigatória

A configuração de cobertura fica no `pom.xml`, com **JaCoCo 0.8.16**. Para rodar a mesma verificação usada no CI, use Java 21:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

A fase `validate` verifica Checkstyle; a fase `test` executa JUnit; `verify` gera o relatório JaCoCo e reprova o build se a cobertura global ficar abaixo de **80% de linhas ou 80% de branches**. Todos os testes também precisam passar. Não basta executar `mvn test` para validar o limite, porque a regra é cobrada em `verify`.

| Arquivo gerado | Uso |
| --- | --- |
| `target/site/jacoco/index.html` | Relatório navegável por pacote, classe e linha |
| `target/site/jacoco/jacoco.xml` | Totais e integração com analisadores |
| `target/jacoco.exec` | Dados de execução coletados pelo agente |
| `target/surefire-reports/` | Resultados de cada teste JUnit |

O JaCoCo mede todas as classes compiladas da aplicação; não há exclusão de services, controllers, DTOs ou configuração de segurança para aumentar a cobertura. `target/` é ignorado pelo Git e não vai para o repositório. A cobertura mede execução de código, não garante ausência de bugs; os testes verificam resultados e erros esperados.

### GitHub Actions

O workflow `.github/workflows/ci.yml` executa:

```text
lint (Checkstyle) → testes + cobertura (clean verify) → empacotamento
```

Os jobs usam `needs`, portanto o empacotamento não começa quando um teste, lint ou limite de cobertura falhar. O último job usa `-DskipTests package`: ele empacota o mesmo código já verificado pelo job anterior, sem tentar validar cobertura em uma máquina sem os dados da execução anterior.

O resumo da execução mostra as porcentagens de linhas e branches. Em **Actions → execução → Artifacts → coverage-and-tests**, baixe o relatório HTML e os resultados JUnit. Os arquivos são publicados também em caso de reprovação e ficam disponíveis por 14 dias. O workflow roda em push para `main` e esta branch de implementação, além de pull requests para `main`.

Os testes não usam credenciais de produção e não fazem login em contas reais. Os testes de segurança fornecem JWTs simulados ao decoder de teste e verificam roles válidas, ausência de roles e claims malformadas. Somente `LEITOR` e `BIBLIOTECARIO`, com os nomes exatos, recebem permissões da aplicação. O marcador de autenticação Bearer do Spring não equivale a uma role administrativa.

O Cypress fica no [repositório do frontend](https://github.com/ksferreira35/bibliogest-frontend), onde testa os fluxos em navegador e acessibilidade. Nesta API, os testes usam JUnit, MockMvc e testes de integração; não é necessário outro repositório nem um navegador para medir a cobertura Java. Referência: [regra check do JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html).

### Segurança e integração com o navegador

O backend valida o token Bearer e aplica as permissões do Spring Security; as regras do frontend não substituem essa validação. Os dados privados continuam em variáveis locais ou Secrets do Kubernetes. `.env` e credenciais reais não devem ser incluídos em commits nem imagens; `.env.example` documenta somente as configurações de desenvolvimento.

O frontend e esta API usam a mesma origem em produção, com caminhos separados no Ingress. Não foi adicionada uma liberação genérica de CORS. Para desenvolvimento, use o proxy do Vite no frontend. Caso a API seja servida em outra origem no futuro, configure apenas as origens autorizadas, mantendo a validação de tokens.

A CSP é entregue pelo Nginx do frontend, junto do documento HTML. Esta API retorna JSON e não injeta a interface nem os controles de acessibilidade. Os READMEs do frontend detalham a política e a auditoria WCAG com Cypress/axe.

### Cenários de teste do Acervo

As suítes verificam cadastro, consulta, edição, exclusão com vínculos, paginação, filtros, sinopse, exemplares, permissões e respostas de erro. Os testes de capas cobrem tamanho/formato/dimensões, armazenamento, caminho inválido e conversão para JPEG. As migrations são executadas sobre H2 em modo PostgreSQL; o teste de descrição também verifica atualização de um esquema com dados anteriores. H2 facilita a execução local e não substitui a validação de migrations no PostgreSQL antes do deploy.

A rotina de cobertura não precisa do PostgreSQL local nem de variáveis de produção. Os testes de integração definem um banco H2 isolado; as capas são gravadas em diretórios temporários.
