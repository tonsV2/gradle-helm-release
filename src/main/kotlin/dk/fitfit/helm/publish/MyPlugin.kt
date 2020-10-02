package dk.fitfit.helm.publish

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("helmPublish", HelmPublishExtension::class.java)
        project.tasks.create("release", MyTask::class.java)
    }
}

open class MyTask : DefaultTask() {
    @TaskAction
    fun execute() {
        val extension: HelmPublishExtension = project.extensions.getByType(HelmPublishExtension::class.java)
        println(extension.chartPath)
    }
}
