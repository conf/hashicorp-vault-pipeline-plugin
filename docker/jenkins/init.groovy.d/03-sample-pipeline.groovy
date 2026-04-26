import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition

def j = Jenkins.get()

if (j.getItem('vault-smoke-test') != null) {
    return
}

def script = '''
pipeline {
    agent any
    environment {
        APP_USER = vault(path: 'secret/myapp', key: 'username', engineVersion: '1')
        APP_PASS = vault(path: 'secret/myapp', key: 'password', engineVersion: '1')
        API_KEY  = vault(path: 'kv2/app',     key: 'api_key',  engineVersion: '2')
    }
    stages {
        stage('Environment block') {
            steps {
                echo "env user=${env.APP_USER}"
                echo "env password length=${env.APP_PASS.length()}"
                echo "env api_key length=${env.API_KEY.length()}"
            }
        }
        stage('Inline step call') {
            steps {
                script {
                    def pw = vault(path: 'secret/myapp', key: 'password', engineVersion: '1')
                    echo "inline password length=${pw.length()}"
                }
            }
        }
        stage('Macro expansion via withEnv') {
            steps {
                withEnv(['APP=myapp']) {
                    script {
                        def v = vault(path: 'secret/${APP}', key: 'username', engineVersion: '1')
                        echo "macro-expanded user=${v}"
                    }
                }
            }
        }
    }
}
'''

def job = j.createProject(WorkflowJob, 'vault-smoke-test')
job.setDefinition(new CpsFlowDefinition(script, true))
job.save()

println '[init] created job vault-smoke-test'
