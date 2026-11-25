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
