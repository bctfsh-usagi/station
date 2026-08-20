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
        // Hive SDK v4 는 Maven Central 에 공개 배포된다.
        // (com.com2us.android.hive:hive-sdk)
        mavenCentral()
    }
}

rootProject.name = "NextStop"
include(":app")
