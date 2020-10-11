package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.service.HelmService
import dk.fitfit.helm.release.service.HelmfileService
import org.gradle.api.tasks.TaskAction
import java.lang.RuntimeException

open class DeployTask : BaseTask() {
    private val helmfileService: HelmfileService = HelmfileService("../nuc-stack/")
    private val helmService: HelmService = HelmService()

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

            val exists = helmService.searchRepo(project.name, version)
            if (!exists) {
//                throw ChartNotFoundInRepositoryException(project.name, version)
                printError("ChartNotFoundInRepositoryException(${project.name}, $version)")
            }

            val currentVersion = helmfileService.getVersion(project.name, environment)
            if (currentVersion != version) {
                helmfileService.update(project.name, environment, version)
                helmfileService.sync(project.name, environment)
                val isDeployed = helmfileService.isDeployed(project.name, environment, version)
                if (isDeployed) {
                    printSuccess("Succesfully deployed version $version of ${project.name} in environment $environment")
                } else {
//                    throw ChartNotDeployedException(project.name, version)
                    printError("ChartNotDeployedException(${project.name}, $version)")
                }
            } else {
                println("Stack already contains version $version of ${project.name} in environment $environment")
            }
        }
    }
}

open class ChartNotFoundInRepositoryException(chart: String, version: String) : RuntimeException("❌ Chart not found in repsitory! ($chart, $version)")
open class ChartNotDeployedException(chart: String, version: String) : RuntimeException("❌ Chart not deployed! ($chart, $version)")
