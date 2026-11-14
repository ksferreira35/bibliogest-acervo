# Implantação do Acervo

O build e o Deployment são independentes de Reservas e do frontend. A API usa `bibliogest_acervo` pelo Service interno `postgres:5432`, com as chaves `username`/`password` do Secret `acervo-db` no namespace `bibliogest`.

Veja [integração PostgreSQL, build e verificação sem escrita](deploy/POSTGRESQL.md), [comandos RKE2](deploy/RKE2.md) e [guia geral](deploy/README.md). A autenticação Keycloak e as permissões existentes são preservadas. O comando de implantação individual não recria bancos nem credenciais.
