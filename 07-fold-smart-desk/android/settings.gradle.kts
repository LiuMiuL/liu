pluginManagement {
    repositories {
        google()
        // Maven Central 공식 미러(GCS). 원본이 429를 돌려줄 때를 대비해 먼저 시도한다.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central 공식 미러(GCS). 원본이 429를 돌려줄 때를 대비해 먼저 시도한다.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "FoldSmartDesk"
include(":app")
