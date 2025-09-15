// settings.gradle.kts (project root)

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")         // ← here
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")         // ← and here
    }
}

rootProject.name = "RescriberKeyboard"
include(":app")
