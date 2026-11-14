#!/usr/bin/env bash
# Biblioteca compartilhada; não modifica kubeconfig nem muda o contexto global.
export KUBECTL_BIN=${KUBECTL_BIN:-kubectl}
k() { "$KUBECTL_BIN" --context "$CONTEXT" --request-timeout=30s "$@"; }
read_config() { python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$CONFIG" "$1"; }
