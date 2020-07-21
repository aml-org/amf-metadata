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
//    stage("Report to Slack") {
//      when {
//        anyOf {
//          branch 'master'
//          branch 'snapshot'
//        }
//      }
//      steps {
//        script {
//          if (!failedStage.isEmpty()) {
//            if (env.BRANCH_NAME == 'master') {
//              color = '#FF0000'
//              headerFlavour = "RED ALERT"
//            } else if (env.BRANCH_NAME == 'snapshot') {
//              color = '#FFD700'
//            }
//            slackSend color: color, channel: "${slackChannel}", message: ":alert: ${headerFlavour}! :alert: Build failed!. \n\tBranch: ${env.BRANCH_NAME}\n\tStage:${failedStage}\n(See ${env.BUILD_URL})\n"
//            currentBuild.status = "FAILURE"
//          } else if (env.BRANCH_NAME == 'master') {
//            slackSend color: '#00FF00', channel: "${slackChannel}", message: ":ok_hand: Master Publish OK! :ok_hand:"
//          }
//        }
//      }
//    }
  }
}
