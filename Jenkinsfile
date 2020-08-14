#!groovy
def slackChannel = '#amf-jenkins'
def failedStage = ""
def color = '#FF8C00'
def headerFlavour = "WARNING"
def artifacts = ""

pipeline {
  agent {
    dockerfile true
  }
  options {
    ansiColor('xterm')
  }
  environment {
    NEXUS = credentials('exchange-nexus')
    GITHUB_ORG = 'aml-org'
    GITHUB_REPO = 'amf-metadata'
  }
  stages {
    stage('Exporters Test') {
      steps {
        script {
          try{
            sh 'sbt -mem 4096 -Dfile.encoding=UTF-8 clean exporters/test'
          } catch (ignored) {
            failedStage = failedStage + " TEST "
            unstable "Failed tests"
          }
        }
      }
    }
    stage('Transform Test') {
      steps {
        script {
          try{
            sh 'sbt -mem 4096 -Dfile.encoding=UTF-8 clean transform/test'
          } catch (ignored) {
            failedStage = failedStage + " TEST "
            unstable "Failed tests"
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
        }
        expression { hasChangesIn("transform", "transform") || isDevelop() }
      }
      steps {
        script {
          try{
            if (failedStage.isEmpty()) {
              sh '''
                  echo "about to publish amf-transform in sbt"
                  sbt transform/publish && echo "sbt publishing of amf-vocabulary successful"
              '''
              artifacts += "[amf-transform]"
            }
          } catch(ignored) {
            failedStage = failedStage + " PUBLISH "
            unstable "Failed publication"
          }
        }
      }
    }
    stage("Report to Slack") {
      when {
        anyOf {
          branch 'master'
          branch 'develop'
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
  def TAG_REF = "refs/tags/$artifact"
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