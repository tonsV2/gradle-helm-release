package dk.fitfit.helm.release.service

import dk.fitfit.helm.release.task.Bash

class GitService(private val path: String = "./") {
    private val bash = Bash()

    fun pull() {
        val gitPullCommand = "git pull"
        bash.exec(gitPullCommand, path)
    }

    fun clone(repo: String, directory: String = "") {
        val gitCloneCommand = if (directory.isNotEmpty()) {
            "git clone $repo $directory"
        } else {
            "git clone $repo"
        }
        bash.exec(gitCloneCommand)
    }

    fun push() {
        val gitPushCommand = "git push"
        bash.exec(gitPushCommand, path)
        val gitPushTagsCommand = "git push --tags"
        bash.exec(gitPushTagsCommand, path)
    }

    fun tag(tag: String) {
        val gitTagCommand = "git tag $tag"
        bash.exec(gitTagCommand, path)
    }

    fun commit(message: String) {
        val gitCommitCommand = "git commit -m '$message'"
        bash.exec(gitCommitCommand, path)
    }

    fun commit(file: String, message: String) {
        val gitCommitCommand = "git commit $file -m '$message'"
        bash.exec(gitCommitCommand, path)
    }

    fun tags(): Set<String> {
        val gitTagsCommand = "git tag --sort=-committerdate"
        return bash.exec(gitTagsCommand, path).toSet()
    }
}
