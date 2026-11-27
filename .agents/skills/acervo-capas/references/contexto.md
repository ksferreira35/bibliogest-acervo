# Contexto técnico — acervo-capas

Trechos temáticos do README da main. Confira o código e o README vigente se o contrato tiver mudado.

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

### Capas e integração

O conteúdo da capa fica no volume persistente; o banco só guarda capa_chave. Upload autenticado de bibliotecário valida formato, tamanho e dimensões; o endpoint público de capa entrega a imagem ao catálogo. Reservas usa listagem de exemplares e resumo de obras por HTTP, e mantém suas próprias informações de circulação. Os manifests compartilhados ficam em [deploy/k8s](../../../../deploy/k8s).
