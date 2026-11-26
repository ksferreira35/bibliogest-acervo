---
name: acervo-catalogo
description: "Implementar regras de categorias, autores, obras, exemplares, descrição e paginação do catálogo."
---

# Acervo Catalogo

- Consulte controllers, DTOs e services de Obra, Autor, Categoria e Exemplar; mantenha IDs UUID, relações e código de tombo único.
- GET /obras retorna content, pagina, tamanho, totalElementos e totalPaginas; página inicia em zero, tamanho máximo 100, ordenação título e ID. Busca considera título, autor, categoria e ISBN. /obras/resumo atende consultas agrupadas de até 100 IDs.
- PUT de obra substitui campos editáveis e mantém ID, capa e exemplares. Descrição opcional até 5000 caracteres; vazios viram null. Exclusão com exemplares retorna 409.
- Acervo mantém catálogo físico; disponibilidade de circulação é calculada em Reservas, que consome as rotas de exemplares e resumo. Preserve os contratos usados pelo frontend.
