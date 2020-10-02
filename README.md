# Gradle test plugin

../gradle-helm-publish/ && ./gradlew clean publishToMavenLocal && ../publish-plugin-test-chart/ && ./gradlew release

https://guides.gradle.org/writing-gradle-plugins/

https://www.praqma.com/stories/gradle-plugin-bootstrap/
https://riptutorial.com/gradle/example/19058/how-to-write-a-standalone-plugin
https://medium.com/@q2ad/custom-gradle-plugin-in-java-5d04866e9e53

https://github.com/bintray/gradle-bintray-plugin#readme
https://mymavenrepo.com/docs/gradle.auth.html


# Release
* Run tests (optional)
* Ensure git is clean
* Bump version in `build.gradle`
* Commit -m 'Bump version to $version'
* Tag commit with 'v$version'
* Save $branch
* Merge into /release
* Push release
* Co $branch

# Deploy
* Run tests
* Deploy from /release
