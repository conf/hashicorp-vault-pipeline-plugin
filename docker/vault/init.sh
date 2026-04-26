#!/bin/sh
set -eux

# KV v1 secret at secret/myapp
vault secrets disable secret 2>/dev/null || true
vault secrets enable -path=secret -version=1 kv
vault kv put secret/myapp username=alice password=s3cr3t

# KV v2 secret at kv2/app
vault secrets enable -path=kv2 -version=2 kv
vault kv put kv2/app api_key=abcdef-0123456789 env=production

echo "[vault-init] seeded test secrets"
