package dk.fitfit.helm.publish

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.create("release", MyTask::class.java)
    }
}

open class MyTask : DefaultTask() {
    @TaskAction
    fun myTask() {
        println("Hello, World!")
    }
}
