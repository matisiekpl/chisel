pipeline {
    agent any

    parameters {
        booleanParam(name: 'PUBLISH', defaultValue: false, description: 'true = wyślij plugin na JetBrains Marketplace')
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false -Dorg.gradle.welcome=never'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './gradlew --no-daemon buildPlugin'
            }
        }

        stage('Verify') {
            steps {
                sh './gradlew --no-daemon verifyPlugin'
            }
        }

        stage('Publish') {
            when {
                expression { params.PUBLISH }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'CHISEL_PUBLISH_TOKEN', variable: 'PUBLISH_TOKEN'),
                    string(credentialsId: 'CHISEL_CERTIFICATE_CHAIN', variable: 'CERTIFICATE_CHAIN'),
                    string(credentialsId: 'CHISEL_PRIVATE_KEY', variable: 'PRIVATE_KEY'),
                    string(credentialsId: 'CHISEL_PRIVATE_KEY_PASSWORD', variable: 'PRIVATE_KEY_PASSWORD'),
                ]) {
                    sh './gradlew --no-daemon publishPlugin'
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/distributions/*.zip', allowEmptyArchive: true, fingerprint: true
            archiveArtifacts artifacts: 'build/reports/pluginVerifier/**', allowEmptyArchive: true
        }
    }
}
