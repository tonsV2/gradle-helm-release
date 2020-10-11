package dk.fitfit.helm.release.task

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
    protected val bash = Bash()

    fun printError(errorMsg: String) {
        System.err.println("❌ $errorMsg")
    }

    fun printSuccess(successMsg: String) {
        println("✅ $successMsg")
    }
}

class BashException(exitCode: Int, val command: String, val output: String) : InvalidExitCodeException(exitCode)
