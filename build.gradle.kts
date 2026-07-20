plugins {
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.13-SNAPSHOT"
    id("org.jetbrains.changelog") version "2.0.0"
    java
}

architectury {
    minecraft = rootProject.extra["minecraft_version"] as String
}

changelog {
    repositoryUrl = rootProject.extra["repository_url"] as String
    introduction = """
        Changelog to track updates for this mod.
        Add your changes to Unreleased if you want to commit.
        Please write according to [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)
    """.trimIndent()
    combinePreReleases = false
}

allprojects {
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")

    if (this != rootProject) {
        apply(plugin = "dev.architectury.loom")

        loom {
            silentMojangMappingsLicense()
        }
    }

    dependencies {
        minecraft("com.mojang:minecraft:${rootProject.extra["minecraft_version"]}")
        mappings(loom.officialMojangMappings())
    }

//    archivesName = rootProject.extra["archives_base_name"]
    version = rootProject.extra["mod_version"] as String
    //group = rootProject.extra["maven_group"] as String

    repositories {
        maven {
            url = uri("https://codeberg.org/api/packages/Mod-Sauce/maven")
        }
        maven {
            url = uri("https://maven.neoforged.net/releases")
        }
        maven {
            url = uri("https://maven.felnull.dev")
        }
        maven {
            url = uri("https://m2.dv8tion.net/releases")
        }
        maven {
            url = uri("https://jitpack.io")
        }
        maven {
            url = uri("https://maven.blamejared.com")
        }
        maven {
            url = uri("https://api.modrinth.com/maven")
        }
        maven {
            url = uri("https://maven.lavalink.dev/releases")
        }
        maven {
            url = uri("https://maven.blamejared.com")
        }
        maven {
            url = uri("https://maven.squiddev.cc")
        }
        maven {
            url = uri("https://maven.ryanhcode.dev/releases")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    java {
        withSourcesJar()
    }
}