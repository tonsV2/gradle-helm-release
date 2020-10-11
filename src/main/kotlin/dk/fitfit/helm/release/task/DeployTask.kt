package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.service.GitService
import dk.fitfit.helm.release.service.HelmService
import dk.fitfit.helm.release.service.HelmfileService
import org.gradle.api.tasks.TaskAction

open class DeployTask : BaseTask() {
    private val helmfileService: HelmfileService = HelmfileService("../nuc-stack/")
    private val helmService: HelmService = HelmService()
    private val gitService = GitService()
    private val projectName = project.name

    @TaskAction
    fun execute() {
        val tags = gitService.tags()

        val environments = tags
                .map { tag -> tag.split("-")[0] }
                .toSet()
                .filter { environment -> helmfileService.getEnvironments().contains(environment) }

        val deployRequests = environments
                .map { environment ->
                    val version = findLatestVersionByEnvironment(tags, environment)
                    DeployRequest(environment, version)
                }
                .filter { alreadyDeployed(it) }

        if (deployRequests.isEmpty()) {
            println("No deployment requests!")
            return
        }

        confirmAllChartsExists(deployRequests)

        deployRequests.forEach {
            val environment = it.environment
            val version = it.version

            helmfileService.updateStack(projectName, environment, version)
            helmfileService.sync(projectName, environment)

            val isDeployed = helmfileService.isDeployed(projectName, environment, version)
            if (isDeployed) {
                printSuccess("Succesfully deployed version $version of $projectName in environment $environment")
            } else {
                throw ChartNotDeployedException(projectName, version)
            }
        }
    }

    private fun findLatestVersionByEnvironment(tags: Set<String>, environment: String): String = tags
            .first { it.startsWith(environment) }
            .split("-")[1]

    private fun alreadyDeployed(deployRequest: DeployRequest): Boolean {
        val currentVersion = helmfileService.getVersion(projectName, deployRequest.environment)
        return currentVersion != deployRequest.version
    }

    private fun confirmAllChartsExists(deployRequests: List<DeployRequest>) {
        deployRequests.forEach {
            val exists = helmService.searchRepo(projectName, it.version)
            if (!exists) {
                throw ChartNotFoundInRepositoryException(projectName, it.version)
            }
        }
    }
}

class DeployRequest(val environment: String, val version: String)

open class ChartNotFoundInRepositoryException(chart: String, version: String) : RuntimeException("❌ Chart not found in repsitory! ($chart, $version)")
open class ChartNotDeployedException(chart: String, version: String) : RuntimeException("❌ Chart not deployed! ($chart, $version)")
