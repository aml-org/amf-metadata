#!groovy
def slackChannel = '#amf-jenkins'
def failedStage = ""
def color = '#FF8C00'
def headerFlavour = "WARNING"
def artifacts = ""
def HAS_PUBLISHED_VOCABULARY = false
def HAS_PUBLISHED_TRANSFORM = false

pipeline {
  agent {
    dockerfile true
  }
  options {
    ansiColor('xterm')
  }
  environment {
    NEXUS = credentials('exchange-nexus')
    NEXUSIQ = credentials('nexus-iq')
    GITHUB_ORG = 'aml-org'
    GITHUB_REPO = 'amf-metadata'
  }
  stages {
    stage('Test') {
      steps {
        script {
          try{
            sh 'sbt -mem 4096 -Dfile.encoding=UTF-8 clean coverage test coverageReport'
          } catch (ignored) {
            failedStage = failedStage + " TEST "
            unstable "Failed tests"
          }
        }
      }
    }
    stage('SonarQube Analysis') {
      when {
        anyOf {
          branch 'master'
          branch 'develop'
        }
      }
      steps {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'sonarqube-official', passwordVariable: 'SONAR_SERVER_TOKEN', usernameVariable: 'SONAR_SERVER_URL']]) {
            script {
              try {
                if (failedStage.isEmpty()) {
                  sh 'sbt -Dsonar.host.url=${SONAR_SERVER_URL} sonarScan'
                }
              } catch (ignored) {
                failedStage = failedStage + " COVERAGE "
                unstable "Failed coverage"
              }
            }
          }
        }
      }
    }
    stage('Publish Vocabulary Artifact') {
      when {
        anyOf {
          branch 'master'
          branch 'develop'
          branch 'remod-breaking'
        }
        expression { hasChangesIn("vocabulary", "vocabulary") || isDevelop() }
      }
      steps {
        script {
          try{
            if (failedStage.isEmpty()) {
              sh '''
                  echo "about to publish in sbt"
                  sbt vocabulary/publish && echo "sbt publishing of amf-vocabulary successful"
              '''
              artifacts += "[amf-vocabulary]"
              HAS_PUBLISHED_VOCABULARY = true
            }
          } catch(ignored) {
            failedStage = failedStage + " PUBLISH "
            unstable "Failed publication"
          }
        }
      }
    }
    stage('Publish Transform Artifact') {
      when {
        anyOf {
          branch 'master'
          branch 'develop'
          branch 'remod-breaking'
          branch 'beta-releases'
        }
        expression { hasChangesIn("transform", "transform") || isDevelop() }
      }
      steps {
        script {
          try{
            if (failedStage.isEmpty()) {
              sh '''
                  echo "about to publish amf-transform in sbt"
                  sbt transform/publish && echo "sbt publishing of amf-transform successful"
              '''
              artifacts += "[amf-transform]"
              HAS_PUBLISHED_TRANSFORM = true
            }
          } catch(ignored) {
            failedStage = failedStage + " PUBLISH "
            unstable "Failed publication"
          }
        }
      }
    }
    stage('Tag published artifacts') {
      when {
        anyOf {
          branch 'master'
          branch 'beta-releases'
        }
      }
      steps {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-salt', passwordVariable: 'GITHUB_PASS', usernameVariable: 'GITHUB_USER']]) {
          script {
            try{
              if (HAS_PUBLISHED_TRANSFORM) {
                String tag = getNextTag("transform")
                tagCommit(tag)
              }
              if (HAS_PUBLISHED_VOCABULARY) {
                String tag = getNextTag("vocabulary")
                tagCommit(tag)
              }
            } catch(ignored) {
              failedStage = failedStage + " TAGGING "
              unstable "Failed tagging"
            }
          }
        }
      }
    }
    stage('Nexus IQ') {
      when {
        anyOf {
          branch 'develop'
        }
      }
      steps {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          script {
            try{
              if (failedStage.isEmpty()){
                sh './gradlew nexusIq'
              }
            } catch(ignored) {
              failedStage = failedStage + " NEXUSIQ "
              unstable "Failed Nexus IQ"
            }
          }
        }
      }
    }
    stage("Report to Slack") {
      when {
        anyOf {
          branch 'master'
          branch 'develop'
          branch 'beta-releases'
        }
      }
      steps {
        script {
          if (!failedStage.isEmpty()) {
            if (env.BRANCH_NAME == 'master') {
              color = '#FF0000'
              headerFlavour = "RED ALERT"
            } else if (env.BRANCH_NAME == 'develop') {
              color = '#FFD700'
            }
            slackSend color: color, channel: "${slackChannel}", message: buildSlackMessage(headerFlavour, env.BRANCH_NAME, failedStage, env.BUILD_URL)
            currentBuild.status = "FAILURE"
          } else if (env.BRANCH_NAME == 'master') {
            slackSend color: '#00FF00', channel: "${slackChannel}", message: ":ok_hand: AMF-METADATA: $artifacts Master Publish OK! :ok_hand:"
          }
        }
      }
    }
  }
}

String buildSlackMessage(headerFlavour, branchName, failedStage, buildUrl) {
  ":alert: $headerFlavour! :alert: AMF-METADATA Build failed!. \n\tBranch: $branchName\n\tStage:$failedStage\n(See $buildUrl)\n"
}

Boolean hasChangesIn(String artifact, String directory) {
  sh '''
       echo "Fetching tags"
       git fetch --tags
     '''
  String TAG_REF = "refs/tags/$artifact"
  def LAST_TAG_COMMIT = sh(returnStdout: true, script: "git for-each-ref $TAG_REF --format='%(objectname)' --sort=-taggerdate --count=1").trim()
  def MASTER_HEAD = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
  echo "Commit sha from $TAG_REF is $LAST_TAG_COMMIT"
  echo "Master HEAD commit SHA: $MASTER_HEAD"
  return sh(
          returnStatus: true,
          script: "git diff --name-only ${LAST_TAG_COMMIT}...${MASTER_HEAD} | grep ${directory}/"
  ) == 0
}

Boolean isDevelop() {
  env.BRANCH_NAME == "develop"
}

String getNextTag(String artifact) {
  String semver = sh(returnStdout: true, script: "sbt $artifact/version | tail -n 1 | grep -o '[0-9].[0-9].[0-9].*'").trim()
  return "$artifact/$semver"
}

String tagCommit(String tag) {
  sh """#!/bin/bash
        echo "about to tag the commit with the new version = $tag"
        git tag $tag
        url="https://\$GITHUB_USER:\$GITHUB_PASS@github.com/\$GITHUB_ORG/\$GITHUB_REPO"
        git push \$url --tags && echo "tagging successful"
     """
}
