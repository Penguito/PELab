pluginManagement {
    repositories {
        google()
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

rootProject.name = "PELab"

include(
    ":app",
    ":render-ui",
    ":render-core-camera",
    ":render-core-permission",
    ":render-core-material",
    ":render-sdk",
)
