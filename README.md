# Gradle Helm Release Plugin

A simple plugin for automating the release a Helm chart.

The code is horrible, pull requests are welcome!

## Motivation

My usual process for releasing a Helm chart involves bumping the version, git committing/tagging/pushing, creating a signed package, posting that to a repository and finally a bit of clean up.

Due to the number of steps involved it makes sense to automate the process. Initially I did so using a bash script but copying that between projects leads to inconsistency.

Since I live in a Java world I thought I'd wrap it up in a gradle plugin.

## Usage
### Configuration
A minimal `build.gradle` file could look like the following

```groovy
buildscript {
    repositories {
        maven { url 'https://dl.bintray.com/tons/tons' }
    }
    dependencies {
        classpath 'dk.fitfit.helm.release:helm-release:1.0.0'
    }
}

apply plugin: 'helm-release'

helmRelease {
    deleteLocalPackage = false
}
```

A slightly more advanced example can be seen [here](https://github.com/tonsV2/surf-screenshotter-chart/blob/eca1559f1ec3c055927f33e7c6bfe75d91968e45/build.gradle).

### Release chart
```bash
./gradlew release
```

## Development
### Release this plugin
```bash
./gradlew bintrayUpload
```

### Local publishing
```bash
./gradlew publishToMavenLocal
```
