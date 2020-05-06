def artifact="target/reptoro-*-fat.jar"

def ocp_map = '/mnt/ocp/jenkins-openshift-mappings.json'
def bc_section = 'build-configs'
def appName = 'reptoro'
def my_bc = null

pipeline {
    agent { label 'maven' }
    stages {
        stage('Prepare') {
            steps {
                sh 'printenv'
            }
        }
        stage('preamble') {
                steps {
                    script {
                        openshift.withCluster() {
                            openshift.withProject() {
                                echo "Using project: ${openshift.project()}"
                            }
                        }
                    }
                }
        }
        stage('Clean Up') {
            steps {
//                 sh 'oc delete svc,dc,route -l app=reptoro'
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            if(openshift.selector('dc',[app:appName]).exists()) {
                                openshift.selector( 'dc',[ app:appName ]).delete()
                            }
                            if(openshift.selector('svc',[app:appName]).exists()) {
                                openshift.selector('svc',[app:appName]).delete()
                            }
                            if(openshift.selector('route',[app:appName]).exists()) {
                                openshift.selector('route',[app:appName]).delete()
                            }
                        }
                    }
                }
            }
        }
        stage('Build') {
            steps {
//                 sh 'mvn -B -V clean verify'
                sh 'rm -rf src/main/generated/src && mvn clean install'
            }
        }
        stage('Create') {
            steps {
                sh "oc expose dc ${appName} --port=8080"
                sh "oc expose service ${appName}"
                sh "oc set triggers dc/${appName} --from-image=${openshift.project()}/${appName}:latest -c vertx"
            }
        }
        stage('Run tests') {
                    parallel {
                        stage('Test run 1') {
                            steps {
                                script {
                                    openshift.withCluster() {
                                        openshift.withProject() {
                                            openshift.selector('dc',appName).describe()
                                        }
                                    }
                                }
                            }
                        }

                        stage('Test run 2') {
                            steps {
                                script {
                                    openshift.withCluster() {
                                        openshift.withProject() {
                                            openshift.selector('svc',appName).describe()
                                        }
                                    }
                                }
                            }
                        }
                    }
        }
        stage('Load OCP Mappings') {
            when {
                allOf {
                    expression { env.CHANGE_ID == null } // Not pull request
                }
            }
            steps {
                echo "Load OCP Mapping document"
                script {
                    def exists = fileExists ocp_map
                    if (exists){
                        def jsonObj = readJSON file: ocp_map
                        if (bc_section in jsonObj){
                            if (env.GIT_URL in jsonObj[bc_section]) {
                                echo "Found BC for Git repo: ${env.GIT_URL}"
                                if (env.BRANCH_NAME in jsonObj[bc_section][env.GIT_URL]) {
                                    my_bc = jsonObj[bc_section][env.GIT_URL][env.BRANCH_NAME]
                                } else {
                                    my_bc = jsonObj[bc_section][env.GIT_URL]['default']
                                }

                                echo "Using BuildConfig: ${my_bc}"
                            }
                            else {
                                echo "Git URL: ${env.GIT_URL} not found in BC mapping."
                            }
                        }
                        else {
                            "BC mapping is invalid! No ${bc_section} sub-object found!"
                        }
                    }
                    else {
                        echo "JSON configuration file not found: ${ocp_map}"
                    }

                    // if ( my_bc == null ) {
                    //     error("No valid BuildConfig reference found for Git URL: ${env.GIT_URL} with branch: ${env.BRANCH_NAME}")
                    // }
                }
            }
        }
        stage('Deploy') {
            when {
                allOf {
                    expression { my_bc != null }
                    expression { env.CHANGE_ID == null } // Not pull request
                    branch 'master'
                }
            }
            steps {
                echo "Deploy"
                sh 'mvn help:effective-settings -B -V deploy -e'
            }
        }
        stage('Archive') {
            steps {
                echo "Archive"
                archiveArtifacts artifacts: "$artifact", fingerprint: true
            }
        }
        stage('Build & Push Image') {
            when {
                allOf {
                    expression { my_bc != null }
                    expression { env.CHANGE_ID == null } // Not pull request
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withProject() {
                            echo "Starting image build: ${openshift.project()}:${my_bc}"
                            def bc = openshift.selector("bc", my_bc)
                            
                            def artifact_file = sh(script: "ls $artifact", returnStdout: true)?.trim()
                            def jar_url = "${BUILD_URL}artifact/$artifact_file"
                            
                            def buildSel = bc.startBuild("-e jar_url=${jar_url}")
                            buildSel.logs("-f")
                        }
                    }
                }
            }
        }
    }
}
