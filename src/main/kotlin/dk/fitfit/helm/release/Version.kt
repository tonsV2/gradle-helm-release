package dk.fitfit.helm.release

import dk.fitfit.helm.release.Version.Fraction.*

data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val qualifier: String?
) {
    fun bump(strategy: Fraction): Version {
        return when (strategy) {
            MAJOR -> Version(major + 1, 0, 0, qualifier)
            MINOR -> Version(major, minor + 1, 0, qualifier)
            PATCH -> Version(major, minor, patch + 1, qualifier)
        }
    }

    enum class Fraction {
        MAJOR, MINOR, PATCH
    }

    override fun toString() = "$major.$minor.$patch" + (qualifier?.let { "-$it" } ?: "")

    companion object {
        fun of(version: String): Version {
            val regex = "(\\d+).(\\d+).(\\d+)(?:-(\\S+))?".toRegex()
            val matchResult = regex.find(version)
                    ?: throw IllegalArgumentException("<$version> is not a valid semantic version qualifier")
            val (major, minor, patch, qualifier) = matchResult.destructured
            return Version(
                    major = major.toInt(),
                    minor = minor.toInt(),
                    patch = patch.toInt(),
                    qualifier = qualifier.ifEmpty { null }
            )
        }
    }
}
