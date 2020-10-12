package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.service.GitService
import dk.fitfit.helm.release.service.HelmService
import dk.fitfit.helm.release.service.HelmfileService
import org.gradle.api.tasks.TaskAction

open class DeployTask : BaseTask() {
    private val gitAppService = GitService()
    private val tempDirectory = createTempDir("gradle-helm-release")
    private val stackPath = tempDirectory.absolutePath + "/"
    private val helmfileService = HelmfileService(stackPath)
    private val gitStackService = GitService(stackPath)
    private val helmService = HelmService()
    private val projectName = project.name

    private fun init() {
        if (extensions.stack.isEmpty()) {
            throw EmptyStackPropertyException()
        }

        gitStackService.clone(extensions.stack, stackPath)
    }

    @TaskAction
    fun execute() {
        init()

        val tags = gitAppService.tags()

        val environments = tags
                .map { tag -> tag.split("-")[0] }
                .toSet()
                .filter { environment -> helmfileService.getEnvironments().contains(environment) }

        val deployRequests = environments
                .map { environment ->
                    val version = findLatestVersionByEnvironment(tags, environment)
                    DeployRequest(environment, version)
                }
                .filter { alreadyInStack(it) }

        if (deployRequests.isEmpty()) {
            println("No deployment requests!")
            return
        }

        confirmChartNotAlreadyDeployed(deployRequests)
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

            val message = "Bump $projectName to version $version in the $environment environment"
            gitStackService.commit("${helmfileService.path}${helmfileService.file}", message)
            gitStackService.push()
        }

        tempDirectory.deleteRecursively()
    }

    private fun confirmChartNotAlreadyDeployed(deployRequests: List<DeployRequest>) {
        deployRequests.forEach {
            val isDeployed = helmfileService.isDeployed(projectName, it.environment, it.version)
            if (isDeployed) {
                throw ChartIsDeployedButNotInStackException(it.environment, it.version)
            }
        }
    }

    private fun findLatestVersionByEnvironment(tags: Set<String>, environment: String): String = tags
            .first { it.startsWith(environment) }
            .split("-")[1]

    private fun alreadyInStack(deployRequest: DeployRequest): Boolean {
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
open class ChartIsDeployedButNotInStackException(environment: String, version: String) : RuntimeException("❌ Chart found in cluster but not in stack! ($environment, $version)")
open class EmptyStackPropertyException : RuntimeException("❌ The stack property needs to be set in order to clone stack")
