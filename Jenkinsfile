#!groovy
def slackChannel = '#amf-jenkins'
def failedStage = ""
def color = '#FF8C00'
def headerFlavour = "WARNING"

pipeline {
  agent {
    dockerfile true
  }
  environment {
    NEXUS = credentials('exchange-nexus')
    GITHUB_ORG = 'aml-org'
    GITHUB_REPO = 'amf-metadata'
  }
  stages {
    stage('Exporters Test') {
      steps {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          script {
            try{
              sh 'sbt -mem 4096 -Dfile.encoding=UTF-8 clean exporters/test'
              sh 'echo "TEST !!!"'
            } catch (ignored) {
              failedStage = failedStage + " TEST "
              unstable "Failed tests"
            }
          }
        }
      }
    }
    stage('Transform Test') {
      steps {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
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
    }
    stage('Publish Transform Artifact') {
      when {
        anyOf {
          branch 'master'
          branch 'develop'
          branch 'jenkins-test'
        }
      }
      steps {
        wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
          script {
            try{
              if (failedStage.isEmpty()) {
                if (hasChangesIn("transform")) {
                  echo " XXX Has changes in Transform! XXX"
                }
                sh '''
                  echo "about to publish in sbt"
                  sbt transform/publish
                  echo "sbt publishing successful"
              '''
              }
            } catch(ignored) {
              failedStage = failedStage + " PUBLISH "
              unstable "Failed publication"
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
            slackSend color: color, channel: "${slackChannel}", message: ":alert: ${headerFlavour}! :alert: Build failed!. \n\tBranch: ${env.BRANCH_NAME}\n\tStage:${failedStage}\n(See ${env.BUILD_URL})\n"
            currentBuild.status = "FAILURE"
          } else if (env.BRANCH_NAME == 'master') {
            slackSend color: '#00FF00', channel: "${slackChannel}", message: ":ok_hand: Master Publish OK! :ok_hand:"
          }
        }
      }
    }
  }
}

def boolean hasChangesIn(String module) {
  if (env.CHANGE_TARGET == null) {
    echo "CHANGE_TARGET not defined"
    return true;
  }

  def MASTER = sh(returnStdout: true, script: "git rev-parse origin/${env.CHANGE_TARGET}").trim()

  // Gets commit hash of HEAD commit. Jenkins will try to merge master into
  // HEAD before running checks. If this is a fast-forward merge, HEAD does
  // not change. If it is not a fast-forward merge, a new commit becomes HEAD
  // so we check for the non-master parent commit hash to get the original
  // HEAD. Jenkins does not save this hash in an environment variable.
  def HEAD = sh(
          returnStdout: true,
          script: "git show -s --no-abbrev-commit --pretty=format:%P%n%H%n HEAD | tr ' ' '\n' | grep -v ${MASTER} | head -n 1"
  ).trim()

  return sh(returnStatus: true, script: "git diff --name-only ${MASTER}...${HEAD} | grep ^${module}/") == 0
}