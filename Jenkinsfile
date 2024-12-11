#!groovy
@Library('amf-jenkins-library') _

import groovy.transform.Field

def SLACK_CHANNEL = '#amf-jenkins'
def PRODUCT_NAME = "amf-metadata"
def lastStage = ""
def color = '#FF8C00'
def headerFlavour = "WARNING"
def HAS_PUBLISHED_VOCABULARY = false
def HAS_PUBLISHED_TRANSFORM = false

pipeline {
    options {
        timeout(time: 30, unit: 'MINUTES')
        ansiColor('xterm')
    }
    agent {
        dockerfile {
            registryCredentialsId 'dockerhub-pro-credentials'
            registryCredentialsId 'github-salt'
            registryUrl 'https://ghcr.io'
        }
    }
    environment {
        NEXUS = credentials('exchange-nexus')
        NEXUSIQ = credentials('nexus-iq')
        GITHUB_ORG = 'aml-org'
        GITHUB_REPO = 'amf-metadata'
        BUILD_NUMBER = "${env.BUILD_NUMBER}"
        BRANCH_NAME = "${env.BRANCH_NAME}"
        CURRENT_VERSION = sh(script: "cat dependencies.properties | grep \"version\" | cut -d '=' -f 2", returnStdout: true)
    }
    stages {
        stage('Test') {
            steps {
                script {
                    lastStage = env.STAGE_NAME
                    sh 'sbt -mem 4096 -Dfile.encoding=UTF-8 clean coverage test coverageAggregate'
                }
            }
        }
        stage('Coverage') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sf-sonarqube-official', passwordVariable: 'SONAR_SERVER_TOKEN', usernameVariable: 'SONAR_SERVER_URL']]) {
                    script {
                        lastStage = env.STAGE_NAME
                        sh 'sbt -Dsonar.host.url=${SONAR_SERVER_URL} -Dsonar.login=${SONAR_SERVER_TOKEN} sonarScan'
                    }
                }
            }
        }
        stage('Publish Vocabulary Artifact') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    lastStage = env.STAGE_NAME
                    sh '''
                          echo "about to publish in sbt"
                          sbt vocabulary/publish && echo "sbt publishing of amf-vocabulary successful"
                       '''
                    HAS_PUBLISHED_VOCABULARY = true
                }
            }
        }
        stage('Publish Transform Artifact') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    lastStage = env.STAGE_NAME
                    sh '''
                          echo "about to publish amf-transform in sbt"
                          sbt transform/publish && echo "sbt publishing of amf-transform successful"
                        '''
                    HAS_PUBLISHED_TRANSFORM = true
                }
            }
        }
        stage('Tag published artifacts') {
            when {
                anyOf {
                    branch 'master'
                }
            }
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-salt', passwordVariable: 'GITHUB_PASS', usernameVariable: 'GITHUB_USER']]) {
                    script {
                        try {
                            if (HAS_PUBLISHED_TRANSFORM) {
                                String tag = getNextTag("transform")
                                tagCommitToGithub(tag)
                            }
                            if (HAS_PUBLISHED_VOCABULARY) {
                                String tag = getNextTag("vocabulary")
                                tagCommitToGithub(tag)
                            }
                        } catch (ignored) {
                            failedStage = failedStage + " TAGGING "
                            unstable "Failed tagging"
                        }
                    }
                }
            }
        }
        // stage('Nexus IQ') {
        //     when {
        //         anyOf {
        //             branch 'master'
        //             branch 'develop'
        //         }
        //     }
        //     steps {
        //         script {
        //             lastStage = env.STAGE_NAME
        //             sh './gradlew nexusIq'
        //         }
        //     }
        // }
    }
    post {
        unsuccessful {
            failureSlackNotify(lastStage, SLACK_CHANNEL, PRODUCT_NAME)
        }
        success {
            successSlackNotify(SLACK_CHANNEL, PRODUCT_NAME)
        }
    }
}

String getNextTag(String artifact) {
    String semver = sbtArtifactVersion(artifact)
    return "$artifact/$semver"
}
