package dk.fitfit.helm.release.task

import net.justmachinery.shellin.bash
import net.justmachinery.shellin.exec.InvalidExitCodeException
import net.justmachinery.shellin.logStderr
import net.justmachinery.shellin.logStdout
import net.justmachinery.shellin.shellin
import org.gradle.api.DefaultTask

abstract class BaseTask : DefaultTask() {
    fun exec(command: String, path: String = ".", debug: Boolean = false): MutableList<String> {
        if (debug) {
            println("Command: $command")
        }

        val shell = shellin {
            logCommands = true
            workingDirectory(path)
        }

        val output = mutableListOf<String>()
        shell.new {
            logStdout {
                { line: CharSequence -> output.add(line.toString()) }
            }

            logStderr {
                { line: CharSequence -> output.add(line.toString()) }
            }

            try {
                bash(command).waitFor()
            } catch (e: InvalidExitCodeException) {
                throw BashException(e.exitCode, command, output.joinToString(System.lineSeparator()))
            }
        }

        return output
    }

    fun printError(errorMsg: String) {
        System.err.println("❌ $errorMsg")
    }

    fun printSuccess(successMsg: String) {
        println("✅ $successMsg")
    }
}

class BashException(exitCode: Int, val command: String, val output: String) : InvalidExitCodeException(exitCode)
