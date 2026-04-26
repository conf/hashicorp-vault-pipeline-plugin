package io.jenkins.plugins.vault;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.datapipe.jenkins.vault.configuration.GlobalVaultConfiguration;
import com.datapipe.jenkins.vault.configuration.VaultConfiguration;
import com.datapipe.jenkins.vault.credentials.VaultTokenCredential;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class VaultReadStepTest {

    private static final String CREDENTIALS_ID = "test-vault-token";
    private static final String VAULT_TOKEN = "test-token";

    @RegisterExtension
    static WireMockExtension vault = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    // JenkinsExtension.resolveParameter calls before() on every call, so request the
    // JenkinsRule exactly once (in @BeforeEach) and reuse it via this field.
    private JenkinsRule jenkins;

    @BeforeEach
    void setup(JenkinsRule jenkins) {
        this.jenkins = jenkins;

        // Production code always calls GlobalVaultConfiguration.get().getConfiguration()
        // even when step provides its own URL, so we must seed it.
        VaultConfiguration config = new VaultConfiguration();
        config.setVaultUrl("http://localhost:" + vault.getPort());
        GlobalVaultConfiguration.get().setConfiguration(config);

        VaultTokenCredential credential = new VaultTokenCredential(
                CredentialsScope.GLOBAL, CREDENTIALS_ID, "Test Token",
                Secret.fromString(VAULT_TOKEN));
        SystemCredentialsProvider.getInstance().getCredentials().add(credential);
    }

    @Test
    void descriptorFunctionNameIsVault() {
        assertEquals("vault", new VaultReadStep.DescriptorImpl().getFunctionName());
    }

    @Test
    void descriptorRequiresRunAndTaskListener() {
        Set<? extends Class<?>> context = new VaultReadStep.DescriptorImpl().getRequiredContext();
        assertTrue(context.contains(Run.class));
        assertTrue(context.contains(TaskListener.class));
        assertEquals(2, context.size());
    }

    @Test
    void readsSecretFromVaultKV1() throws Exception {
        vault.stubFor(get(urlEqualTo("/v1/secret/myapp"))
                .willReturn(okJson("{\"request_id\":\"test\",\"data\":{\"password\":\"s3cr3t\"}}")));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-kv1");
        job.setDefinition(new CpsFlowDefinition(String.format(
                "def val = vault(path: 'secret/myapp', key: 'password',"
                + " vaultUrl: 'http://localhost:%d', credentialsId: '%s', engineVersion: '1')\n"
                + "echo \"RESULT:${val}\"",
                vault.getPort(), CREDENTIALS_ID), true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("RESULT:s3cr3t", run);
    }

    @Test
    void readsSecretFromVaultKV2() throws Exception {
        vault.stubFor(get(urlEqualTo("/v1/secret/data/myapp"))
                .willReturn(okJson("{\"request_id\":\"test\","
                        + "\"data\":{\"data\":{\"password\":\"s3cr3t\"},\"metadata\":{}}}")));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-kv2");
        job.setDefinition(new CpsFlowDefinition(String.format(
                "def val = vault(path: 'secret/myapp', key: 'password',"
                + " vaultUrl: 'http://localhost:%d', credentialsId: '%s', engineVersion: '2')\n"
                + "echo \"RESULT:${val}\"",
                vault.getPort(), CREDENTIALS_ID), true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("RESULT:s3cr3t", run);
    }

    @Test
    void usesGlobalVaultUrlWhenStepUrlOmitted() throws Exception {
        vault.stubFor(get(urlEqualTo("/v1/secret/global"))
                .willReturn(okJson("{\"request_id\":\"test\",\"data\":{\"token\":\"globalval\"}}")));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-global");
        job.setDefinition(new CpsFlowDefinition(String.format(
                "def val = vault(path: 'secret/global', key: 'token',"
                + " credentialsId: '%s', engineVersion: '1')\n"
                + "echo \"RESULT:${val}\"",
                CREDENTIALS_ID), true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("RESULT:globalval", run);
    }

    @Test
    void expandsMacrosInPath() throws Exception {
        vault.stubFor(get(urlEqualTo("/v1/myapp/config"))
                .willReturn(okJson("{\"request_id\":\"test\",\"data\":{\"key\":\"macrovalue\"}}")));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-macros");
        job.setDefinition(new CpsFlowDefinition(String.format(
                "withEnv(['APP=myapp']) {\n"
                + "  def val = vault(path: '${APP}/config', key: 'key',"
                + "    vaultUrl: 'http://localhost:%d', credentialsId: '%s', engineVersion: '1')\n"
                + "  echo \"RESULT:${val}\"\n"
                + "}",
                vault.getPort(), CREDENTIALS_ID), true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("RESULT:macrovalue", run);
    }

    @Test
    void failsWhenVaultReturns403() throws Exception {
        vault.stubFor(any(anyUrl())
                .willReturn(aResponse().withStatus(403)
                        .withBody("{\"errors\":[\"permission denied\"]}")));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-403");
        job.setDefinition(new CpsFlowDefinition(String.format(
                "vault(path: 'secret/forbidden', key: 'key',"
                + " vaultUrl: 'http://localhost:%d', credentialsId: '%s', engineVersion: '1')",
                vault.getPort(), CREDENTIALS_ID), true));

        WorkflowRun run = job.scheduleBuild2(0).get();
        assertEquals(Result.FAILURE, run.getResult());
    }
}
