---
name: acervo-seguranca-jwt
description: "Manter autenticação e autorização defensiva Spring Security/Keycloak na API de acervo."
---

# Acervo Seguranca Jwt

- Consulte `config/SecurityConfiguration.java` e teste de roles. A API valida JWT Bearer, emissor, assinatura, audiência bibliogest-acervo e validade; converta apenas roles reconhecidas.
- Consultas autorizadas usam LEITOR/BIBLIOTECARIO; operações administrativas exigem BIBLIOTECARIO.
- Preserve controles de autorização no backend mesmo se a interface esconder comandos. Valide entradas, parametrização SQL, mensagens de erro sem dados internos e acesso a registros de acordo com o perfil.
- Use variáveis e referências a Secrets para credenciais; não exponha JWTs ou segredos em logs, documentação ou testes. Não habilite Swagger em produção sem decisão explícita.
