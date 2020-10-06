package dk.fitfit.helm.release

open class GitExtension(
        var requireCleanWorkingDirectory: Boolean = true,
        var tag: Boolean = true,
        var commit: Boolean = true,
        var push: Boolean = true
)

open class SignatureExtension(
        var key: String = "",
        var keyStore: String = "~/.gnupg/pubring.gpg"
)

open class RepositoryExtension(
        var url: String = "",
        var username: String = "",
        var password: String = ""
)

open class HelmReleaseExtension(
        var debug: Boolean = false,
        var chartPath: String = ".",
        var overrideChartVersion: String = "",
        var overrideAppVersion: String = "",
        var bumpVersion: Boolean = true,
        var deleteLocalPackage: Boolean = true,
        var git: GitExtension = GitExtension(),
        var signature: SignatureExtension = SignatureExtension(),
        var repository: RepositoryExtension = RepositoryExtension()
)
