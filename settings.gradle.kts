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
        // Pleos Vehicle SDK
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
    }
}

rootProject.name = "Sunstone"

// 기능 구현: 차 없이도 돌아가는 순수 Kotlin. 루틴, 실행 엔진, 가짜 차.
include(":routines")
// 앱 연결: Pleos SDK·화면·음성·Gleo·상황 감지를 루틴에 이어 붙인다.
include(":app")
