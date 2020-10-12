package dk.fitfit.helm.release.task

import dk.fitfit.helm.release.service.GitService
import dk.fitfit.helm.release.service.HelmService
import dk.fitfit.helm.release.service.HelmfileService
import org.gradle.api.tasks.TaskAction

open class DeployTask : BaseTask() {
    private val gitAppService = GitService()
    private val tempDirectory = createTempDir("gradle-helm-release-")
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

        deployRequests.forEach {
            confirmChartExists(it)

            val environment = it.environment
            val version = it.version

            if (!alreadyInStack(it)) {
                helmfileService.updateStack(projectName, environment, version)
                printSuccess("$projectName updated to version $version in $environment")

                if (isDeleteRequest(it)) {
                    val message = "Uninstall $projectName from $environment"
                    gitStackService.commit(helmfileService.file, message)
                } else {
                    val message = "Update $projectName to version $version in $environment"
                    gitStackService.commit(helmfileService.file, message)
                }
                printSuccess("Stack committed")

                gitStackService.push()
                printSuccess("Stack pushed")
            }

            if (!alreadyInCluster(it)) {
                helmfileService.sync(projectName, environment)

                val isDeployed = alreadyInCluster(it)

                if (isDeleteRequest(it)) {
                    if (!isDeployed) {
                        printSuccess("$projectName undeployed from $environment")
                    } else {
                        // TODO
                        printError("Wrong!!!")
                    }
                } else {
                    if (isDeployed) {
                        printSuccess("Version $version of $projectName deployed to $environment")
                    } else {
                        throw ChartNotDeployedException(projectName, version)
                    }
                }
            }
        }

        tempDirectory.deleteRecursively()
    }

    private fun confirmChartExists(deployRequest: DeployRequest) {
        if (isDeleteRequest(deployRequest)) {
            return
        }

        val exists = helmService.searchRepo(projectName, deployRequest.version)
        if (!exists) {
            throw ChartNotFoundInRepositoryException(projectName, deployRequest.version)
        }
    }

    private fun findLatestVersionByEnvironment(tags: Set<String>, environment: String): String = tags
            .first { it.startsWith(environment) }
            .split("-")[1]

    private fun alreadyInStack(deployRequest: DeployRequest): Boolean {
        val currentVersion = helmfileService.getVersion(projectName, deployRequest.environment)
        return deployRequest.version == currentVersion
    }

    private fun alreadyInCluster(deployRequest: DeployRequest): Boolean = helmfileService.isDeployed(projectName, deployRequest.environment, deployRequest.version)

    private fun isDeleteRequest(deployRequest: DeployRequest) = deployRequest.version == "0"
}

class DeployRequest(val environment: String, val version: String)

open class ChartNotFoundInRepositoryException(chart: String, version: String) : RuntimeException("❌ Chart not found in repository! ($chart, $version)")
open class ChartNotDeployedException(chart: String, version: String) : RuntimeException("❌ Chart not deployed! ($chart, $version)")
open class ChartIsDeployedButNotInStackException(environment: String, version: String) : RuntimeException("❌ Chart found in cluster but not in stack! ($environment, $version)")
open class EmptyStackPropertyException : RuntimeException("❌ The stack property needs to be set in order to clone stack")
