---
name: acervo-capas
description: "Manter upload de capas, validação de imagens e armazenamento persistente do Acervo."
---

# Acervo Capas

- Consulte CapaController, CapaService e ArmazenamentoDeCapas, além de CapaApiTests. Upload por PUT /obras/{obraId}/capa com multipart arquivo exige BIBLIOTECARIO.
- Aceite apenas formatos/documentos definidos no contrato (JPEG/PNG), até 5 MB e 2048 × 2048 pixels. Valide conteúdo e dimensões, não apenas extensão; caminho é determinado pela obra.
- Preserve capaUrl e a leitura pública em /acervo/capas/. O diretório local é configurável com BIBLIOGEST_CAPAS_DIR; no RKE2 há PVC montado em /data para persistir capas entre rollouts.
- Teste substituição, rejeição de arquivo inválido, leitura e tratamento de falhas sem alterar arquivos reais. Não inclua capas gravadas de produção no Git.
