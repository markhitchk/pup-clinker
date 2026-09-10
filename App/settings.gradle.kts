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

// Keep release identity consistent before the Android app build script is evaluated.
// This avoids post-build manifest edits and ensures aapt compiles the requested version.
val appBuildFile = file("app/build.gradle.kts")
val appBuildSource = appBuildFile.readText()
val versionedAppBuildSource = appBuildSource
    .replace("versionCode = 20", "versionCode = 22")
    .replace("versionName = \"1.7.7\"", "versionName = \"1.7.9\"")
check(versionedAppBuildSource != appBuildSource) {
    "Unable to apply Puppy Clicker 1.7.9 (22) release version before project evaluation"
}
appBuildFile.writeText(versionedAppBuildSource)

rootProject.name = "PuppyClicker"
include(":app")
