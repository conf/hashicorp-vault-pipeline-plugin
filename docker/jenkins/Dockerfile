FROM jenkins/jenkins:2.555.1-jdk21

USER root

COPY docker/jenkins/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli -f /usr/share/jenkins/ref/plugins.txt

# Our plugin — .jpi extension is what Jenkins expects in plugins dir
COPY target/hashicorp-vault-pipeline.hpi /usr/share/jenkins/ref/plugins/hashicorp-vault-pipeline.jpi

COPY docker/jenkins/init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/

USER jenkins
