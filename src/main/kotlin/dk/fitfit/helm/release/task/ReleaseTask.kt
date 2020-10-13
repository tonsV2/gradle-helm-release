package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.*
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

open class ReleaseTask : BaseTask() {
    private val versionRegex = "version: (\\S+)".toRegex()
    private val nameRegex = "name: (\\S+)".toRegex()

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

        val chartName = extractChartName()
        printSuccess("Chart name extracted: $chartName")

        if (extensions.overrideChartVersion.isEmpty()) {
            val chartVersionString = extractChartVersion()
            printSuccess("Chart version extracted: $chartVersionString")
            chartVersion = Version.of(chartVersionString)
                    .bump(extensions.bumpStrategy)
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
                gitCommit()
                printSuccess("Git commit")
            }

            if (extensions.git.tag) {
                gitTag()
                printSuccess("Git tag")
            }

            createChartPackage()
            printSuccess("Chart packaged")

            if (extensions.repository.url.isNotEmpty()) {
                postChart(chartName)
                printSuccess("Chart package posted to repository")
            }

            if (extensions.deleteLocalPackage) {
                deleteLocalPackage(chartName)
                printSuccess("Local package deleted")
            }

            if (extensions.git.push) {
                gitPush()
                printSuccess("Git push")
                gitPushTags()
                printSuccess("Git push tags")
            }
        } catch (e: BashException) {
            printError("Command: ${e.command}")
            printError("Output: ${e.output}")
            printError("${e.message}")
        }
    }

    private fun gitPushTags() {
        val gitPushTagsCommand = "git push --tags"
        bash.exec(gitPushTagsCommand)
    }

    private fun gitPush() {
        val gitPushCommand = "git push"
        bash.exec(gitPushCommand)
    }

    private fun deleteLocalPackage(chartName: String) {
        val packagePath = "${extensions.chartPath}/$chartName-$chartVersion.tgz"
        Files.delete(Paths.get(packagePath))

        val provenancePath = "${extensions.chartPath}/$chartName-$chartVersion.tgz.prov"
        Files.deleteIfExists(Paths.get(provenancePath))
    }

    private fun postChart(chartName: String) {
        // TODO: trim extensions.repository.password from output
        val postChartCommand = if (extensions.repository.username.isNotEmpty() && extensions.repository.password.isNotEmpty()) {
            """
                curl --fail --user "${extensions.repository.username}:${extensions.repository.password}" \
                    -F "chart=@$chartName-$chartVersion.tgz" \
                    -F "prov=@$chartName-$chartVersion.tgz.prov" \
                    ${extensions.repository.url}
            """.trimIndent()
        } else {
            """
                curl --fail -F "chart=@$chartName-$chartVersion.tgz" \
                    -F "prov=@$chartName-$chartVersion.tgz.prov" \
                    ${extensions.repository.url}
            """.trimIndent()
        }
        bash.exec(postChartCommand)
    }

    private fun createChartPackage() {
        val overrideChartVersion = if (extensions.overrideChartVersion.isNotEmpty()) {
            "--version ${extensions.overrideChartVersion} "
        } else ""

        val overrideAppVersion = if (extensions.overrideAppVersion.isNotEmpty()) {
            "--app-version ${extensions.overrideAppVersion} "
        } else ""

        val helmPackageCommand = if (extensions.signature.key.isNotEmpty() && extensions.signature.keyStore.isNotEmpty()) {
            "helm package $overrideChartVersion$overrideAppVersion--sign --key '${extensions.signature.key}' --keyring ${extensions.signature.keyStore} ${extensions.chartPath}"
        } else {
            "helm package $overrideChartVersion$overrideAppVersion${extensions.chartPath}"
        }
        bash.exec(helmPackageCommand)
    }

    private fun gitTag() {
        // TODO: Custom tag... Make the tag customizable through an extension property
        val gitTagCommand = "git tag \"RELEASE-$chartVersion\""
        bash.exec(gitTagCommand)
    }

    private fun gitCommit() {
        // TODO: Custom commit message... Make the commit message customizable through an extension property
        val gitCommitCommand = "git commit $chartPath -m \"Bump version\""
        bash.exec(gitCommitCommand)
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

    private fun extractChartName(): String {
        val nameMatchResult = nameRegex.find(chartFileContent)
                ?: throw IllegalArgumentException("name property not found in $chartPath")
        return nameMatchResult.destructured.component1()
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
