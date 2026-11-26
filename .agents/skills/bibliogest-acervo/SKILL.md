---
name: bibliogest-acervo
description: "Desenvolver e revisar a API de acervo do BiblioGest, catálogo, autores, categorias, exemplares e capas."
---

# BiblioGest — Acervo

## Antes de alterar

- Leia o README, o workflow de CI e os arquivos de configuração da branch atual. A main pode estar atrás da versão implantada; confirme a base antes de implementar ou integrar alterações.
- Mantenha os contratos REST, os códigos HTTP e a autorização existentes; alterações de contrato precisam ser coordenadas com os consumidores.
- Preserve segredos em variáveis de ambiente ou referências a Secrets. Use valores fictícios nos exemplos e testes; não registre tokens, senhas ou dumps.
- Deploy em RKE2: valide os manifests, use imagens com tags identificáveis e confira prontidão após publicação autorizada. Não modifique produção ao desenvolver ou testar localmente.

## Domínio de acervo

- Acervo é responsável pelo catálogo e exemplares físicos. Reservas controla a circulação; não replique o estado de reserva no catálogo sem uma decisão de arquitetura.
- Preserve as relações entre obra, autor, categoria e exemplar e a unicidade do código de tombo. Use DTOs para entrada e saída.
- Operações de cadastro, edição e exclusão exigem autorização de bibliotecário. Leitura segue as regras do contrato existente.
- Preserve armazenamento persistente das capas e valide os arquivos enviados. Quando disponíveis, mantenha paginação e descrição opcional.
- Revise a compatibilidade das rotas consumidas por Reservas e frontend antes de mudar respostas.
## Desenvolvimento e validação

- Use Java 21 e Maven, preserve as camadas controller, DTO, service e repository. Valide entradas e mantenha os erros padronizados.
- Use migrations Flyway incrementais, sem reescrever migrations já aplicadas. Utilize banco isolado para desenvolvimento e Testcontainers quando disponível.
- Mantenha autenticação JWT e autorização no backend. Valide emissor, audiência, expiração e roles conforme o contrato do serviço.
- Execute `./mvnw clean verify` (ou `mvn clean verify`). Quando a branch incluir JaCoCo e Checkstyle, preserve a cobertura mínima de 80% de linhas e branches e as verificações de lint. Swagger somente no perfil local.
