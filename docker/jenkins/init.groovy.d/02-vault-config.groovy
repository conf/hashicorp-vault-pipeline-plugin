import jenkins.model.Jenkins
import hudson.util.Secret
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.datapipe.jenkins.vault.credentials.VaultTokenCredential
import com.datapipe.jenkins.vault.configuration.GlobalVaultConfiguration
import com.datapipe.jenkins.vault.configuration.VaultConfiguration

def credential = new VaultTokenCredential(
    CredentialsScope.GLOBAL,
    'vault-token',
    'Dev Vault root token',
    Secret.fromString('root'))

SystemCredentialsProvider.getInstance().getCredentials().add(credential)
SystemCredentialsProvider.getInstance().save()

def config = new VaultConfiguration()
config.setVaultUrl('http://vault:8200')
config.setVaultCredentialId('vault-token')
config.setEngineVersion(2)

def global = GlobalVaultConfiguration.get()
global.setConfiguration(config)
global.save()

println '[init] vault configured: http://vault:8200, credential vault-token'
