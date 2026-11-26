---
name: acervo-testes-ci
description: "Validar a API de acervo com JUnit, MockMvc, Mockito, Checkstyle, JaCoCo e GitHub Actions."
---

# Acervo Testes Ci

- Execute `./mvnw --batch-mode --no-transfer-progress clean verify` em Java 21. Checkstyle roda em validate, testes em test, cobertura em verify; JaCoCo exige 80% em linhas e branches. Consulte `pom.xml`.
- Use JUnit/MockMvc para resultados, erros e permissões; mocks para integrações externas. Claims de teste são fictícias, e não substituem validação real de JWT em produção.
- Acervo usa testes isolados de obra, exemplar, capa, migrations e roles; confira as configurações H2/integração existentes.
- CI: lint → clean verify → package, com needs. Inspecione `target/site/jacoco/index.html` e `target/surefire-reports/`. Não exclua classes nem ignore falhas para atingir o limite.
