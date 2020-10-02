package dk.fitfit.helm.release

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class HelmReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("helmRelease", HelmReleaseExtension::class.java)
        project.tasks.create("release", ReleaseTask::class.java)
    }
}

open class ReleaseTask : DefaultTask() {
    @TaskAction
    fun execute() {
        val extension: HelmReleaseExtension = project.extensions.getByType(HelmReleaseExtension::class.java)
        println(extension.chartPath)
    }
}
