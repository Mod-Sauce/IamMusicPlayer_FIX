pluginManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        gradlePluginPortal()
    }
}

include("common")
include("fabric")
include("neoforge")

rootProject.name = "IamMusicPlayer"

val oeDir = file("../OtyacraftEngineRenewed")

if (oeDir.exists()) {
    includeBuild(oeDir)
}
