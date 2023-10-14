#!groovy
def BN = (BRANCH_NAME == 'master' || BRANCH_NAME.startsWith('releases/')) ? BRANCH_NAME : 'releases/2023-10'

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
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
                "knime-chromium",
                "knime-core",
                "knime-core-ui",
                "knime-distance",
                "knime-ensembles",
                "knime-gateway",
                "knime-js-base",
                "knime-json",
                "knime-kerberos",
                "knime-email",
                "knime-reporting2",
                "knime-rest",
                "knime-xml"
            ],
            ius: [
                "org.knime.features.reporting2.feature.group", 
                "org.knime.features.core.feature.group",
                "org.knime.features.base.feature.group",
                "org.knime.features.base.views.feature.group",
                "org.knime.features.browser.chromium.feature.group",
                "org.knime.features.browser.cef.feature.group",
                "org.knime.features.quickform.legacy.feature.group",
                "org.knime.features.ensembles.feature.group",
                "org.knime.features.distmatrix.feature.group",
                "org.knime.features.xml.feature.group",
                "org.knime.features.gateway.feature.group"
            ]
        ]
	)

/*
	stage('Sonarqube analysis') {
		env.lastStage = env.STAGE_NAME
		workflowTests.runSonar()
	}
*/
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}
/* vim: set shiftwidth=4 expandtab smarttab: */
