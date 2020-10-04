package dk.fitfit.helm.release

import net.justmachinery.shellin.bash
import net.justmachinery.shellin.exec.InvalidExitCodeException
import net.justmachinery.shellin.logStderr
import net.justmachinery.shellin.logStdout
import net.justmachinery.shellin.shellin
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import java.io.File

class HelmReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("helmRelease", HelmReleaseExtension::class.java)
        project.extensions.create("signature", SignatureExtension::class.java)
        project.extensions.create("git", GitExtension::class.java)
        project.extensions.create("repository", RepositoryExtension::class.java)
        project.tasks.create("release", ReleaseTask::class.java)
    }
}

open class ReleaseTask : DefaultTask() {
    // TODO: Should be an extension value
    private val bumpStrategy = Version.Fraction.MINOR

    private val versionRegex = "version: (\\S+)".toRegex()
    private val nameRegex = "name: (\\S+)".toRegex()

    private val extensions = mergeExtensions()

    private var chartPath: String? = null
    private lateinit var chartFile: File
    private lateinit var chartFileContent: String

    @TaskAction
    fun execute() {

        if (extensions.debug) {
            printExtensionVariables()
        }

        if (!extensions.ignoreCleanWorkingDirectory && cleanWorkingDirectory()) {
            throw IllegalStateException("Working directory not clean")
        }

        readChart()
        println("✅ Chart read")

        val chartName = extractChartName()
        println("✅ Chart name extracted: $chartName")
        val chartVersionString = extractChartVersion()
        println("✅ Chart version extracted: $chartVersionString")
        val chartVersion = Version.of(chartVersionString)
                .bump(bumpStrategy)
        println("✅ Version bumped: $chartVersion")

        if (extensions.bumpVersion) {
            writeBackVersion(chartVersion)
            println("✅ Chart.yaml updated with new version: $chartVersion")
        }

        try {
            if (extensions.git.commit) {
                gitCommit()
                println("✅ Git commit done!")
            }

            if (extensions.git.tag) {
                gitTag(chartVersion)
                println("✅ Git tag done!")
            }

            createChartPackage()
            println("✅ Chart package created!")

            if (extensions.repository.url.isNotEmpty()) {
                postChart(chartName, chartVersion)
                println("✅ Chart package posted to repository!")
            }

            if (extensions.deleteLocalPackage) {
                deleteLocalPackage(chartName, chartVersion)
                println("✅ Local package deleted!")
            }

            if (extensions.git.push) {
                gitPush()
                println("✅ Git push!")
                gitPushTags()
                println("✅ Git tags pushed!")
            }
        } catch (e: BashException) {
            printErr("❌ Command: ${e.command}")
            printErr("❌ Output: ${e.output}")
            printErr("❌ ${e.message}")
        }
    }

    private fun gitPushTags() {
        val gitPushTagsCommand = "git push --tags"
        exec(gitPushTagsCommand)
    }

    private fun gitPush() {
        val gitPushCommand = "git push"
        exec(gitPushCommand)
    }

    private fun deleteLocalPackage(chartName: String, chartVersion: Version) {
        // TODO: Use java.nio.file.Files
        val rmPackageCommand = "rm ${extensions.chartPath}/$chartName-$chartVersion.tgz"
        exec(rmPackageCommand)

        if (File("${extensions.chartPath}/$chartName-$chartVersion.tgz.prov").isFile) {
            val rmProvenanceCommand = "rm ${extensions.chartPath}/$chartName-$chartVersion.tgz.prov"
            exec(rmProvenanceCommand)
        }
    }

    private fun postChart(chartName: String, chartVersion: Version) {
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
        exec(postChartCommand)

        /*
                    val url = extension.repository.url
                    val tgz = "$chartName-$chartVersion.tgz"
                    val tgzPath = "${extension.chartPath}/$tgz"

                    val request = Fuel.upload(url)

                    request.add(
                            FileDataPart(File(tgzPath), name = "chart", filename = "$tgz"),
                            FileDataPart(File("$tgzPath.prov"), name = "prov", filename = "$tgz.prov")
                    )

                    if (extension.repository.username.isNotEmpty() && extension.repository.password.isNotEmpty()) {
                        request.authentication().basic(extension.repository.username, extension.repository.password)
                    }

                    request.response { result ->
                        println(result)
                    }
        */
    }

    private fun createChartPackage() {
        val overrideChartVersion = if (extensions.overrideChartVersion.isNotEmpty()) {
            "--version ${extensions.overrideChartVersion} "
        } else ""

        val overrideAppVersion = if (extensions.overrideAppVersion.isNotEmpty()) {
            "--version ${extensions.overrideAppVersion} "
        } else ""

        val helmPackageCommand = if (extensions.signature.key.isNotEmpty() && extensions.signature.keyStore.isNotEmpty()) {
            "helm package $overrideChartVersion$overrideAppVersion--sign --key '${extensions.signature.key}' --keyring ${extensions.signature.keyStore} ${extensions.chartPath}"
        } else {
            "helm package $overrideChartVersion$overrideAppVersion${extensions.chartPath}"
        }
        exec(helmPackageCommand)
    }

    private fun gitTag(chartVersion: Version) {
        // TODO: Custom tag... Make the tag customizable through an extension property
        val gitTagCommand = "git tag \"RELEASE-$chartVersion\""
        exec(gitTagCommand)
    }

    private fun gitCommit() {
        // TODO: Custom commit message... Make the commit message customizable through an extension property
        val gitCommitCommand = "git commit $chartPath -m \"Bump version\""
        exec(gitCommitCommand)
    }

    private fun writeBackVersion(chartVersion: Version) {
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

    private fun printExtensionVariables() {
        val outputExtension = """
            Extension values:
            debug: ${extensions.debug}
            chartPath: ${extensions.chartPath}
            bumpVersion: ${extensions.bumpVersion}
    
            git.tag: ${extensions.git.tag}
            git.commit: ${extensions.git.commit}
            git.push: ${extensions.git.push}
    
            signature.key: ${extensions.signature.key}
            signature.keyStore: ${extensions.signature.keyStore}
    
            repository.url: ${extensions.repository.url}
            repository.username: ${extensions.repository.username}
            repository.password: ${extensions.repository.password}
    
            deleteLocalPackage: ${extensions.deleteLocalPackage}
            """.trimIndent()
        println(outputExtension)
    }

    private fun cleanWorkingDirectory(): Boolean {
        val command = "git status --porcelain"
        val output = exec(command)
        return output.isNotEmpty()
    }

    private fun exec(command: String): String {
        if (extensions.debug) {
            println("Command: $command")
        }

        val shell = shellin {
            logCommands = true
            workingDirectory(extensions.chartPath)
        }

        var output = ""
        shell.new {
            logStdout {
                { line: CharSequence -> output += line }
            }

            logStderr {
                { line: CharSequence -> output += line }
            }

            try {
                bash(command).waitFor()
            } catch (e: InvalidExitCodeException) {
                throw BashException(e.exitCode, command, output)
            }
        }

        return output
    }

    private fun mergeExtensions(): HelmReleaseExtension {
        val extensions: HelmReleaseExtension = project.extensions.getByType(HelmReleaseExtension::class.java)

        val gitExtension: GitExtension = project.extensions.getByType(GitExtension::class.java)
        val signatureExtension: SignatureExtension = project.extensions.getByType(SignatureExtension::class.java)
        val repositoryExtension: RepositoryExtension = project.extensions.getByType(RepositoryExtension::class.java)

        extensions.git = gitExtension
        extensions.signature = signatureExtension
        extensions.repository = repositoryExtension

        return extensions
    }
}

open class BashException(exitCode: Int, val command: String, val output: String) : InvalidExitCodeException(exitCode)

fun printErr(errorMsg: String) {
    System.err.println(errorMsg)
}
