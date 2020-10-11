package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.service.helmfile.HelmfileService
import org.gradle.api.tasks.TaskAction

open class DeployTask : BaseTask() {
    private val helmfileService: HelmfileService = HelmfileService("../nuc-stack/")

    @TaskAction
    fun execute() {
        val gitTagsCommand = "git tag --sort=-committerdate"
        val tags = bash.exec(gitTagsCommand)

        val environments = tags
                .map { it.split("-")[0] }
                .toSet()
                .filter {
                    helmfileService.getEnvironments().contains(it)
                }

        val targets = mutableMapOf<String, String>()
        environments.forEach { environment ->
            val version = tags
                    .first { it.startsWith(environment) }
                    .split("-")[1]
            targets[environment] = version
        }

        targets.forEach {
            val environment = it.key
            val version = it.value
            // TODO: Assert version exists
            // https://helm.sh/docs/helm/helm_list/
            val currentVersion = helmfileService.getVersion(project.name, environment)
            if (currentVersion != version) {
                helmfileService.update(project.name, environment, version)
                helmfileService.sync(project.name, environment)
                // TODO: Assert success
                // https://helm.sh/docs/helm/helm_status/
            } else {
                println("Stack already contains requested version ($environment, $version)")
            }
        }
    }
}
