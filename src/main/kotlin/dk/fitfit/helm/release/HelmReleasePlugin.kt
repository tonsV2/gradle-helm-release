package dk.fitfit.helm.release

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.extensions.authentication
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
import java.nio.file.Files
import java.nio.file.Paths

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
        println("✅ Chart read")

        val chartName = extractChartName()
        println("✅ Chart name extracted: $chartName")

        if (extensions.overrideChartVersion.isEmpty()) {
            val chartVersionString = extractChartVersion()
            println("✅ Chart version extracted: $chartVersionString")
            chartVersion = Version.of(chartVersionString)
                    .bump(bumpStrategy)
            println("✅ Version bumped: $chartVersion")
        } else {
            chartVersion = Version.of(extensions.overrideChartVersion)
        }

        if (extensions.overrideChartVersion.isEmpty() && extensions.bumpVersion) {
            writeBackVersion()
            println("✅ Chart.yaml updated with new version: $chartVersion")
        }

        try {
            if (extensions.git.commit) {
                gitCommit()
                println("✅ Git commit done!")
            }

            if (extensions.git.tag) {
                gitTag()
                println("✅ Git tag done!")
            }

            createChartPackage()
            println("✅ Chart package created!")

            if (extensions.repository.url.isNotEmpty()) {
                postChart(chartName)
                println("✅ Chart package posted to repository!")
            }

            if (extensions.deleteLocalPackage) {
                deleteLocalPackage(chartName)
                println("✅ Local package deleted!")
            }

            if (extensions.git.push) {
                gitPush()
                println("✅ Git push!")
                gitPushTags()
                println("✅ Git push tags!")
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

    private fun deleteLocalPackage(chartName: String) {
        val packagePath = "${extensions.chartPath}/$chartName-$chartVersion.tgz"
        Files.delete(Paths.get(packagePath))

        val provenancePath = "${extensions.chartPath}/$chartName-$chartVersion.tgz.prov"
        Files.deleteIfExists(Paths.get(provenancePath))
    }

    private fun postChart(chartName: String) {
        val chartPackage = "$chartPath/$chartName-$chartVersion.tgz"

        val request = Fuel.upload(extensions.repository.url)

        request.add(FileDataPart(File(chartPackage), name = "chart", filename = chartPackage))
        if (extensions.signature.key.isNotEmpty()) {
            request.add(FileDataPart(File("$chartPackage.prov"), name = "prov", filename = "$chartPackage.prov"))
        }

        val username = extensions.repository.username
        val password = extensions.repository.password
        if (username.isNotEmpty() && password.isNotEmpty()) {
            request.authentication().basic(username, password)
        }

        request.response { result ->
            println(result)
        }
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

    private fun gitTag() {
        // TODO: Custom tag... Make the tag customizable through an extension property
        val gitTagCommand = "git tag \"RELEASE-$chartVersion\""
        exec(gitTagCommand)
    }

    private fun gitCommit() {
        // TODO: Custom commit message... Make the commit message customizable through an extension property
        val gitCommitCommand = "git commit $chartPath -m \"Bump version\""
        exec(gitCommitCommand)
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
