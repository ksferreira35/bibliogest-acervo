---
name: acervo-postgres-flyway
description: "Manter persistência PostgreSQL, schema e migrations Flyway da API de acervo."
---

# Acervo Postgres Flyway

- Leia `src/main/resources/db/migration/`, `application.yaml` e os repositories. Use banco próprio de desenvolvimento, credenciais via ambiente e consultas parametrizadas.
- Spring Data JPA: preserve relações categoria/autor/obra/exemplar, índice/unique de tombo e colunas opcionais de capa/descrição. Valide esquema pelo Hibernate.
- Novas mudanças usam migrations incrementais; não altere migrations aplicadas. Preserve dados anteriores e teste evolução de schema com banco isolado.
- Confira resultados de Flyway e prontidão da API. Nunca use produção como banco de teste; backup e alterações operacionais exigem autorização própria.
