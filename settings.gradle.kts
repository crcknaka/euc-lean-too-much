pluginManagement {
    repositories {
        google()
        // gdx-teavm's Gradle plugin is published to Maven Central, not the Plugin Portal
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "euc-lean-too-much"

include("core")
include("android")
include("lwjgl3")
include("web")
