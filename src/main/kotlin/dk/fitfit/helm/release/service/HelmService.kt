package dk.fitfit.helm.release.service

import dk.fitfit.helm.release.Version
import dk.fitfit.helm.release.task.Bash
import org.yaml.snakeyaml.Yaml
import java.io.File

class HelmService(private val chartPath: String = "./") {
    private lateinit var chartFileContent: String
    private lateinit var chartFile: File

    private val versionRegex = "version: (\\S+)".toRegex()

    fun readChart(): String {
        val chartFilePath = "${chartPath}Chart.yaml"
        chartFile = File(chartFilePath)
        chartFileContent = if (!chartFile.isFile) {
            throw IllegalStateException("$chartFilePath not found")
        } else {
            chartFile.readText()
        }
        return chartFilePath
    }

    fun extractChartVersion(): String {
        val versionMatchResult = chartFileContent?.let { versionRegex.find(it) }
                ?: throw IllegalArgumentException("version property not found in $chartPath")
        return versionMatchResult.destructured.component1()
    }

    fun writeBackVersion(chartVersion: Version) {
        val updatedChartFileContent = chartFileContent.replaceFirst(versionRegex, "version: $chartVersion")
        chartFile.writeText(updatedChartFileContent)
    }

    companion object {
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
}
