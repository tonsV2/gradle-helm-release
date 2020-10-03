package dk.fitfit.helm.release

import net.justmachinery.shellin.bash
import net.justmachinery.shellin.collectStdout
import net.justmachinery.shellin.exec.InvalidExitCodeException
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

    private val extensions = mergeExtensions()

    @TaskAction
    fun execute() {

        if (extensions.debug) {
            printExtensionVariables()
        }

        if (cleanWorkingDirectory()) {
//            throw IllegalStateException("Working directory not clean")
        }

        // Read chart file
        val chartPath = "${extensions.chartPath}/Chart.yaml"
        val chartFile = File(chartPath)
        val chartFileContent = if (!chartFile.isFile) {
            throw IllegalStateException("$chartPath not found. Consider specifying ")
        } else {
            chartFile.readText()
        }

        // Extract name
        val nameRegex = "name: (\\S+)".toRegex()
        val nameMatchResult = nameRegex.find(chartFileContent)
                ?: throw IllegalArgumentException("name property not found in $chartPath")
        val chartName = nameMatchResult.destructured.component1()

        // Extract version
        val versionRegex = "version: (\\S+)".toRegex()
        val versionMatchResult = versionRegex.find(chartFileContent)
                ?: throw IllegalArgumentException("version property not found in $chartPath")
        val chartVersionString = versionMatchResult.destructured.component1()

        // Bump version
        val chartVersion = Version.of(chartVersionString).bump(bumpStrategy)

        // Replace version in file
        if (extensions.bumpVersion) {
            val replaceFirst = chartFileContent.replaceFirst(versionRegex, "version: $chartVersion")
            chartFile.writeText(replaceFirst)
        }

        // Commit version bump
        if (extensions.git.commit) {
            val gitCommitCommand = "git commit $chartPath -m \"Bump version\""
            exec(gitCommitCommand)
        }

        // Git tag
        if (extensions.git.tag) {
            val gitTagCommand = "git tag \"RELEASE-$chartVersion\""
            exec(gitTagCommand)
        }

        // Package
        if (extensions.signature.key.isNotEmpty() && extensions.signature.keyStore.isNotEmpty()) {
            val helmSignedPackageCommand = "helm package --sign --key '${extensions.signature.key}' --keyring ${extensions.signature.keyStore} ${extensions.chartPath}"
            exec(helmSignedPackageCommand)
        } else {
            val helmPackageCommand = "helm package ${extensions.chartPath}"
            exec(helmPackageCommand)
        }

        // Post chart
        if (extensions.repository.url.isNotEmpty()) {
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

        // Remove package and provenance file
        if (extensions.deleteLocalPackage) {
            val rmPackageCommand = "rm ${extensions.chartPath}/$chartName-$chartVersion.tgz"
            exec(rmPackageCommand)

            if (File("${extensions.chartPath}/$chartName-$chartVersion.tgz.prov").isFile) {
                val rmProvenanceCommand = "rm ${extensions.chartPath}/$chartName-$chartVersion.tgz.prov"
                exec(rmProvenanceCommand)
            }
        }

        // Git push
        if (extensions.git.push) {
            val gitPushCommand = "git push"
            exec(gitPushCommand)
        }

        // Git push tags
        if (extensions.git.push) {
            val gitPushTagsCommand = "git push --tags"
            exec(gitPushTagsCommand)
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
            try {
                output = collectStdout {
                    bash(command).waitFor()
                }.text
            } catch (e: InvalidExitCodeException) {
                println(e)
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
