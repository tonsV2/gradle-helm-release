package dk.fitfit.helm.release.service

import com.google.common.base.CaseFormat
import dk.fitfit.helm.release.task.Bash
import org.yaml.snakeyaml.Yaml
import java.io.File

class HelmfileService(private val path: String = "./", private val file: String = "helmfile.yaml") {
    private val yaml = Yaml()
    private val bash = Bash()
    private val helmfile = File("$path$file")

    fun getEnvironments(): Set<String> {
        val inputStream = File("$path$file").inputStream()
        val obj: Map<String, Map<String, Any>> = yaml.load(inputStream)
        return obj["environments"]?.keys ?: throw EnvironmentsNotFoundException("$path$file")
    }

    fun getVersion(projectName: String, environment: String): String {
        val inputStream = File("$path$file").inputStream()
        val obj: Map<String, Map<String, Map<String, List<Map<String, Map<String, Any>>>>>> = yaml.load(inputStream)
        val values = obj["environments"]?.get(environment)?.get("values")
        val releases = values?.first { it.keys.contains(projectNameLowerCamel(projectName)) }
        return releases?.get(projectNameLowerCamel(projectName))?.get("version").toString()
    }

    // Inspiration: https://stackoverflow.com/a/64298773/672009
    private fun getReplacePropertyRegex(projectName: String, environment: String, property: String) = """(environments:
            |(?:\R\h{2}.*)*?\R\h{2}$environment:
            |(?:\R\h{4}.*)*?\R\h{6}-\h*${projectNameLowerCamel(projectName)}:
            |(?:\R\h{10}.*)*?\R\h{10}$property:\h*)\S+(.*)
        """.trimMargin().toRegex(RegexOption.COMMENTS)

    fun updateStack(projectName: String, environment: String, version: String) {
        val helmfileContent = helmfile.readText()

        val versionRegex = getReplacePropertyRegex(projectName, environment, "version")
        val versionUpdatedHelmfile = versionRegex.replaceFirst(helmfileContent, "$1$version$2")

        val installedRegex = getReplacePropertyRegex(projectName, environment, "installed")
        val installed = version != "0"
        val updatedHelmfile = installedRegex.replaceFirst(versionUpdatedHelmfile, "$1$installed$2")

        helmfile.writeText(updatedHelmfile)

        // TODO: Git commit
    }

    fun sync(projectName: String, environment: String) {
        val syncCommand = "helmfile -e $environment --selector name=$projectName sync"
        bash.exec(syncCommand, path)
    }

    fun isDeployed(projectName: String, environment: String, version: String): Boolean {
        val helmfileListCommand = "helmfile -e $environment list --output json"
        val output = bash.exec(helmfileListCommand, path)
        val obj: List<Map<String, Any>> = yaml.load(output[0])
        val first = obj.first {
            it["name"] == projectName && it["enabled"].toString().toBoolean()
        }

        val name = first["name"]
        val namespace = first["namespace"]
// TODO: Move to HelmService... Or merge HelmfileService and HelmService?
        val helmListCommand = "helm list -n $namespace --filter '$name' -o json"
        val helmListOutput = bash.exec(helmListCommand, path)

        val helmListOutputObj: List<Map<String, String>> = yaml.load(helmListOutput[0])
        val chart = helmListOutputObj[0]["chart"]
        val status = helmListOutputObj[0]["status"]
        return chart == "$projectName-$version" && status == "deployed"
    }

    private fun projectNameLowerCamel(projectName: String) = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, projectName)
}

open class EnvironmentsNotFoundException(targetFile: String) : RuntimeException("The environments property could not be extracted from $targetFile")
