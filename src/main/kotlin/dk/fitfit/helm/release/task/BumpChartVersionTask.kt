package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.Version
import dk.fitfit.helm.release.service.GitService
import org.gradle.api.tasks.TaskAction
import java.io.File

open class BumpChartVersionTask : BaseTask() {
    private val bumpStrategy = Version.Fraction.MINOR
    private val gitService = GitService()

    private val versionRegex = "version: (\\S+)".toRegex()

    private lateinit var chartPath: String
    private lateinit var chartFile: File
    private lateinit var chartFileContent: String
    private lateinit var chartVersion: Version

    @TaskAction
    fun execute() {
        if (extensions.debug) {
            printExtensionVariables()
        }

        if (extensions.git.requireCleanWorkingDirectory && cleanWorkingDirectory()) {
            throw IllegalStateException("Working directory not clean")
        }

        readChart()
        printSuccess("Chart read")

        if (extensions.overrideChartVersion.isEmpty()) {
            val chartVersionString = extractChartVersion()
            printSuccess("Chart version extracted: $chartVersionString")
            chartVersion = Version.of(chartVersionString)
                    .bump(bumpStrategy)
            printSuccess("Version bumped: $chartVersion")
        } else {
            chartVersion = Version.of(extensions.overrideChartVersion)
        }

        if (extensions.overrideChartVersion.isEmpty() && extensions.bumpVersion) {
            writeBackVersion()
            printSuccess("Chart.yaml updated with version: $chartVersion")
        }

        try {
            if (extensions.git.commit) {
                gitService.commit("RELEASE-$chartVersion")
                printSuccess("Git commit")
            }

            if (extensions.git.tag) {
                gitService.tag("RELEASE-$chartVersion")
                printSuccess("Git tag")
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

    private fun writeBackVersion() {
        val replaceFirst = chartFileContent.replaceFirst(versionRegex, "version: $chartVersion")
        chartFile.writeText(replaceFirst)
    }

    private fun extractChartVersion(): String {
        val versionMatchResult = versionRegex.find(chartFileContent)
                ?: throw IllegalArgumentException("version property not found in $chartPath")
        return versionMatchResult.destructured.component1()
    }

    private fun readChart() {
        chartPath = "${extensions.chartPath}/Chart.yaml"
        chartFile = File(chartPath)
        chartFileContent = if (!chartFile.isFile) {
            throw IllegalStateException("$chartPath not found. Consider specifying ")
        } else {
            chartFile.readText()
        }
    }
}
