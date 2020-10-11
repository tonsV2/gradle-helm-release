package dk.fitfit.helm.release.service

import dk.fitfit.helm.release.task.Bash

class GitService(private val path: String = ".") {
    private val bash = Bash()

    fun tags(): Set<String> {
        val gitTagsCommand = "git tag --sort=-committerdate"
        return bash.exec(gitTagsCommand, path).toSet()
    }
}
