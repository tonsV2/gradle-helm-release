package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.Version
import dk.fitfit.helm.release.service.GitService
import dk.fitfit.helm.release.service.HelmService
import org.gradle.api.tasks.TaskAction

open class BumpChartVersionTask : BaseTask() {
    // TODO: Should be an extension property
    private val bumpStrategy = Version.Fraction.MINOR
    private val gitService = GitService()
    private lateinit var helmService: HelmService

    @TaskAction
    fun execute() {
        helmService = HelmService(extensions.chartPath)

        if (extensions.debug) {
            printExtensionVariables()
        }

        if (extensions.git.requireCleanWorkingDirectory && cleanWorkingDirectory()) {
            throw IllegalStateException("Working directory not clean")
        }

        val chartFile = helmService.readChart()
        printSuccess("Chart file read ($chartFile)")

        val chartVersion = if (extensions.overrideChartVersion.isEmpty()) {
            val chartVersionString = helmService.extractChartVersion()
            Version.of(chartVersionString).bump(bumpStrategy)
        } else {
            Version.of(extensions.overrideChartVersion)
        }

        if (extensions.overrideChartVersion.isEmpty() && extensions.bumpVersion) {
            helmService.writeBackVersion(chartVersion)
            printSuccess("Chart.yaml updated with version: $chartVersion")
        }

        try {
            if (extensions.git.commit) {
                val message = "Chart version bumped to $chartVersion"
                gitService.commit(chartFile, message)
                printSuccess("Git commit: $message")
            }

            if (extensions.git.tag) {
                val tag = "RELEASE-chart-$chartVersion"
                gitService.tag(tag)
                printSuccess("Git tag: $tag")
            }

            if (extensions.git.push) {
                gitService.push()
                printSuccess("Git push")
            }
        } catch (e: BashException) {
            printError("Command: ${e.command}")
            printError("Output: ${e.output}")
            printError("${e.message}")
        }
    }
}
