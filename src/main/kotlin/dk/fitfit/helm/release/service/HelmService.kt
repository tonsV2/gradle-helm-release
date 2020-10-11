package dk.fitfit.helm.release.service

import dk.fitfit.helm.release.task.Bash
import org.yaml.snakeyaml.Yaml

class HelmService {
    private val yaml = Yaml()
    private val bash = Bash()

    fun searchRepo(chart: String, version: String? = null): Boolean {
        val searchCommand = "helm search repo -l $chart -o json"
        val output = bash.exec(searchCommand)
        val charts: List<Map<String, String>> = yaml.load(output[0])
        return if (version == null) {
            charts.isNotEmpty()
        } else {
            charts.any { it["version"] == version }
        }
    }

}
