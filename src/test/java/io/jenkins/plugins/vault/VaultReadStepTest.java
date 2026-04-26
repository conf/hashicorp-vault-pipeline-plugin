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

    // ---------- tests ----------

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
        stubKv1("secret/myapp", "password", "s3cr3t");

        WorkflowRun run = runPipeline("test-kv1",
                assignToDescription(vaultStep("secret/myapp", "password", "1")));

        assertEquals("s3cr3t", run.getDescription());
    }

    @Test
    void readsSecretFromVaultKV2() throws Exception {
        stubKv2("secret/myapp", "password", "s3cr3t");

        WorkflowRun run = runPipeline("test-kv2",
                assignToDescription(vaultStep("secret/myapp", "password", "2")));

        assertEquals("s3cr3t", run.getDescription());
    }

    @Test
    void usesGlobalVaultUrlWhenStepUrlOmitted() throws Exception {
        stubKv1("secret/global", "token", "globalval");

        WorkflowRun run = runPipeline("test-global",
                assignToDescription(vaultStepNoUrl("secret/global", "token", "1")));

        assertEquals("globalval", run.getDescription());
    }

    @Test
    void expandsMacrosInPath() throws Exception {
        stubKv1("myapp/config", "key", "macrovalue");

        WorkflowRun run = runPipeline("test-macros",
                "withEnv(['APP=myapp']) {\n"
                        + "  " + assignToDescription(vaultStep("${APP}/config", "key", "1")) + "\n"
                        + "}");

        assertEquals("macrovalue", run.getDescription());
    }

    @Test
    void failsWhenVaultReturns403() throws Exception {
        vault.stubFor(any(anyUrl())
                .willReturn(aResponse().withStatus(403)
                        .withBody("{\"errors\":[\"permission denied\"]}")));

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-403");
        job.setDefinition(new CpsFlowDefinition(
                vaultStep("secret/forbidden", "key", "1"), true));

        WorkflowRun run = job.scheduleBuild2(0).get();
        assertEquals(Result.FAILURE, run.getResult());
    }

    // ---------- helpers ----------

    /** Stub a KV v1 Vault read: {@code GET /v1/<path>} returning {@code {key: value}}. */
    private void stubKv1(String path, String key, String value) {
        vault.stubFor(get(urlEqualTo("/v1/" + path))
                .willReturn(okJson(String.format(
                        "{\"request_id\":\"t\",\"data\":{\"%s\":\"%s\"}}",
                        key, value))));
    }

    /** Stub a KV v2 Vault read: {@code GET /v1/<mount>/data/<rest>} with nested data wrapper. */
    private void stubKv2(String path, String key, String value) {
        int sep = path.indexOf('/');
        String urlPath = "/v1/" + path.substring(0, sep) + "/data/" + path.substring(sep + 1);
        vault.stubFor(get(urlEqualTo(urlPath))
                .willReturn(okJson(String.format(
                        "{\"request_id\":\"t\",\"data\":{\"data\":{\"%s\":\"%s\"},\"metadata\":{}}}",
                        key, value))));
    }

    /** A {@code vault(...)} step call with the test vault URL + credentials. */
    private String vaultStep(String path, String key, String engineVersion) {
        return String.format(
                "vault(path: '%s', key: '%s',"
                        + " vaultUrl: 'http://localhost:%d',"
                        + " credentialsId: '%s', engineVersion: '%s')",
                path, key, vault.getPort(), CREDENTIALS_ID, engineVersion);
    }

    /** A {@code vault(...)} step call that omits vaultUrl (forces fallback to global config). */
    private String vaultStepNoUrl(String path, String key, String engineVersion) {
        return String.format(
                "vault(path: '%s', key: '%s',"
                        + " credentialsId: '%s', engineVersion: '%s')",
                path, key, CREDENTIALS_ID, engineVersion);
    }

    /** Create a pipeline job with the given script and run it, expecting success. */
    private WorkflowRun runPipeline(String jobName, String script) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, jobName);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return jenkins.buildAndAssertSuccess(job);
    }

    /** Assignments to currentBuild.description bypass the console log filter — useful for
     *  asserting the exact value returned by vault() without fighting secret masking. */
    private String assignToDescription(String stepExpression) {
        return "currentBuild.description = " + stepExpression;
    }
}
