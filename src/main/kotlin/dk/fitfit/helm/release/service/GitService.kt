package dk.fitfit.helm.release.service

import dk.fitfit.helm.release.task.Bash

class GitService(private val path: String = "./") {
    private val bash = Bash()

    fun push(): List<String> {
        val gitPushCommand = "git push"
        return bash.exec(gitPushCommand, path)
    }

    fun commit(file: String, message: String): List<String> {
        val gitCommitCommand = "git commit $file -m '$message'"
        return bash.exec(gitCommitCommand, path)
    }

    fun tags(): Set<String> {
        val gitTagsCommand = "git tag --sort=-committerdate"
        return bash.exec(gitTagsCommand, path).toSet()
    }
}
