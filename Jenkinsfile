#!groovy
def BN = (BRANCH_NAME == 'master' || BRANCH_NAME.startsWith('releases/')) ? BRANCH_NAME : 'releases/2026-06'

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
        upstream('knime-reporting2/' + env.BRANCH_NAME.replaceAll('/', '%2F')),
        upstream('knime-json/' + env.BRANCH_NAME.replaceAll('/', '%2F'))
    ]),
    parameters(workflowTests.getConfigurationsAsParameters()),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.email')

    workflowTests.runTests(
        dependencies: [
            repositories: [
                "knime-credentials-base",
                "knime-gateway",
                "knime-base", 
                "knime-base-views",
                "knime-cef",
                "knime-cloud",
                "knime-core",
                "knime-core-ui",
                "knime-distance",
                "knime-ensembles",
                "knime-filehandling",
                "knime-gateway",
                'knime-google',
                "knime-js-base",
                "knime-json",
                "knime-kerberos",
                "knime-email",
                "knime-reporting2",
                "knime-rest",
                "knime-scripting-editor",
                "knime-xml"
            ],
            ius: [
                "org.knime.email.tests",
                "org.knime.features.reporting2.feature.group", 
                "org.knime.features.browser.cef.feature.group",
            ]
        ]
    )

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar()
    }

} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}
/* vim: set shiftwidth=4 expandtab smarttab: */
