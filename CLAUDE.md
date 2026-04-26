# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Jenkins Pipeline plugin that adds a `vault()` step function for reading secrets from HashiCorp Vault into pipeline environment variables. Packaged as HPI (Jenkins plugin format).

## Build Commands

```bash
# Build HPI package
mvn clean package

# Run tests (none currently exist)
mvn test

# Static analysis (SpotBugs — configured to fail on error)
mvn spotbugs:check

# Skip tests during build
mvn clean package -DskipTests
```

## Architecture

Single file plugin: `src/main/java/io/jenkins/plugins/vault/VaultReadStep.java`

Three nested classes:
- **`VaultReadStep`** — pipeline step definition; DataBound fields: `path`, `key`, `credentialsId`, `vaultUrl`, `engineVersion`
- **`VaultStepExecution`** — execution logic; resolves global vs per-step config, calls `VaultAccessor.read()`, returns secret value
- **`DescriptorImpl`** — registers the step as `"vault"` in Jenkins

### Execution flow

1. Pipeline calls `vault(path: '...', key: '...')`
2. `VaultStepExecution.start()` loads `GlobalVaultConfiguration.get()` for defaults
3. Per-step params override globals; `Util.replaceMacro()` expands `${ENV_VAR}` references
4. `VaultAccessor` initialized with resolved URL + credentials → calls `.read(path, engineVersion)`
5. Key extracted from response, returned via `getContext().onSuccess(value)`

### Key dependencies

- `hashicorp-vault-plugin` — provides `VaultAccessor`, `VaultConfig`, `VaultCredential`, `GlobalVaultConfiguration`
- `workflow-step-api` — Jenkins pipeline step framework
- Jenkins parent POM v4.40 — handles all build/packaging infrastructure

## Pipeline Usage

```groovy
// Using global Vault configuration
def secret = vault(path: 'secret/myapp', key: 'password')

// Per-step configuration (overrides global)
def secret = vault(
    path: 'secret/myapp',
    key: 'password',
    vaultUrl: 'https://vault.example.com',
    credentialsId: 'vault-token',
    engineVersion: '2'
)

// Mask secrets in console output
wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[password: secret]]]) {
    sh "use ${secret}"
}
```

## Notes

- No test suite exists — integration testing requires a running Jenkins + Vault instance
- SpotBugs is configured with `Max` effort and `failOnError=true` — static analysis must pass
- Engine version: `'1'` for KV v1, `'2'` for KV v2
