package dk.fitfit.helm.release

import dk.fitfit.helm.release.task.BumpChartVersionTask
import dk.fitfit.helm.release.task.DeployTask
import dk.fitfit.helm.release.task.ReleaseTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class HelmReleasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("helmRelease", HelmReleaseExtension::class.java)
        project.extensions.create("signature", SignatureExtension::class.java)
        project.extensions.create("git", GitExtension::class.java)
        project.extensions.create("repository", RepositoryExtension::class.java)

        project.tasks.create("release", ReleaseTask::class.java)
        project.tasks.create("deploy", DeployTask::class.java)
        project.tasks.create("bumpChartVersion", BumpChartVersionTask::class.java)
    }
}
