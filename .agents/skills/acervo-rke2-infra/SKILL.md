---
name: acervo-rke2-infra
description: "Manter os manifests compartilhados do BiblioGest: Kubernetes RKE2, Traefik, PostgreSQL, Keycloak, PVCs e frontend."
---

# Acervo Rke2 Infra

- Consulte deploy/k8s/kustomization.yaml e manifests numerados: namespace/config, NetworkPolicies, PostgreSQL, Keycloak, Acervo, Reservas, frontend, pgAdmin e Ingress. Usuários possui manifest próprio no seu repo.
- Cluster RKE2 usa containerd e Traefik; serviços internos usam ClusterIP, PostgreSQL possui bancos/usuários separados, PVC preserva dados e capas. Preserve escopo de rede, SCRAM, isolamento e regras de autenticação.
- Imagens Docker com tags identificáveis; ConfigMaps para configuração pública, Secrets para valores confidenciais. Não coloque configs renderizadas, credenciais ou dumps no Git.
- Para alteração autorizada de deploy, valide diferenças em relação ao estado atual, faça backup apropriado, preserve os recursos compartilhados e verifique rollout/prontidão. Não reconstrua ou reconfigure outros serviços por engano.
