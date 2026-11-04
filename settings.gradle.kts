pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CashMemer"

include(":core")
include(":app")
include(":wear")

// libs/filewall-kit is a git submodule. If it was not checked out, Gradle sees an empty project and
// fails later with "No matching variant of project :filewall-kit was found". Say what to do instead.
val kitDir = file("libs/filewall-kit")
if (kitDir.listFiles()?.any { it.name.startsWith("build.gradle") } != true) {
    throw GradleException(
        "libs/filewall-kit is empty. In the project folder run:  git submodule update --init --recursive  " +
            "then use File > Sync Project with Gradle Files.",
    )
}
include(":filewall-kit")
project(":filewall-kit").projectDir = file("libs/filewall-kit")
