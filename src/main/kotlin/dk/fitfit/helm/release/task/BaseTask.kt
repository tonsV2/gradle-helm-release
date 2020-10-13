package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.GitExtension
import dk.fitfit.helm.release.HelmReleaseExtension
import dk.fitfit.helm.release.RepositoryExtension
import dk.fitfit.helm.release.SignatureExtension
import net.justmachinery.shellin.*
import net.justmachinery.shellin.exec.InvalidExitCodeException
import org.gradle.api.DefaultTask

class Bash {
    fun exec(command: String, path: String = ".", debug: Boolean = false): List<String> {
        if (debug) {
            println("Command: $command")
        }

        val shell = shellin {
            logCommands = true
            workingDirectory(path)
        }

        val output = mutableListOf<String>()

        shell.new {
            try {
                collectStdout {
                    bash(command).waitFor()
                }.stream.use {
                    it.reader().forEachLine { output.add(it) }
                }
            } catch (e: InvalidExitCodeException) {
                throw BashException(e.exitCode, command, output.joinToString(System.lineSeparator()))
            }
        }

        return output
    }
}

abstract class BaseTask : DefaultTask() {
    protected val extensions = mergeExtensions()
    protected val bash = Bash()

    fun printExtensionVariables() {
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

    fun cleanWorkingDirectory(): Boolean {
        val command = "git status --porcelain"
        val output = bash.exec(command)
        return output.isNotEmpty()
    }

    fun printError(errorMsg: String) {
        System.err.println("❌ $errorMsg")
    }

    fun printSuccess(successMsg: String) {
        println("✅ $successMsg")
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

class BashException(exitCode: Int, val command: String, val output: String) : InvalidExitCodeException(exitCode)
