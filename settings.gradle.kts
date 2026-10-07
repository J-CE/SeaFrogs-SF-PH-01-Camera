pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("androidx(\\..*)?")
                includeGroupByRegex("com\\.google\\.testing\\.platform(\\..*)?")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("androidx(\\..*)?")
                includeGroupByRegex("com\\.google\\.testing\\.platform(\\..*)?")
            }
        }
        mavenCentral()
    }
}
rootProject.name = "SeaFrogs-SF-PH-01-Camera"
include(":app")
