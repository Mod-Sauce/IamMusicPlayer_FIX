import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "9.4.1"
    id("dev.architectury.loom") version "1.13-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.115"
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
    runs {
        register("datagen") {
            data()
            name("Minecraft Data")
            programArgs("--mod", "iammusicplayer", "--all", "--output", file("src/generated/resources/").absolutePath, "--existing", file("src/main/resources/").absolutePath)
        }
    }
}

configurations.create("common")
configurations.create("shadowCommon")
configurations.create("shadowIn")

configurations.compileClasspath.get().extendsFrom(configurations.getByName("common"))
configurations.runtimeClasspath.get().extendsFrom(configurations.getByName("common"))
configurations.named("developmentNeoForge") { extendsFrom(configurations.getByName("common")) }
configurations.getByName("shadowCommon").extendsFrom(configurations.getByName("shadowIn"))

repositories {
    mavenCentral()
    maven("https://cursemaven.com")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/snapshots")
}

dependencies {
    neoForge("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")
    modApi("dev.architectury:architectury-neoforge:${rootProject.property("architectury_version")}")

    add("common", project(":common", configuration = "namedElements")) { isTransitive = false }
    add("shadowCommon", project(":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    val localJar = file("../../OtyacraftEngineRenewed/neoforge/build/libs/otyacraftenginerenewed-neoforge-mc1.21.1-${rootProject.property("oe_version")}.jar")
    if (localJar.exists()) {
        modApi(files(localJar))
    } else {
        modApi("org.mod-sauce:otyacraftenginerenewed-neoforge:${rootProject.property("oe_version")}")
    }

    modApi("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}")
    modRuntimeOnly("me.shedaniel:RoughlyEnoughItems-neoforge:${rootProject.property("rei_version")}")
    modApi("maven.modrinth:sound-physics-remastered:neoforge-${rootProject.property("sound_physics_remastered")}")
    modCompileOnly("curse.maven:touhou-little-maid-355044:8061852")
    modApi("vazkii.patchouli:Patchouli:${rootProject.property("patchouli_version")}-NEOFORGE")

    modCompileOnly("com.simibubi.create:create-${rootProject.property("minecraft_version")}:${property("create_version")}:slim") { isTransitive = false }
    modCompileOnly("net.createmod.ponder:Ponder-NeoForge-${rootProject.property("minecraft_version")}:${property("ponder_version")}")
    modCompileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-${rootProject.property("minecraft_version")}:${property("flywheel_version")}")
    modCompileOnly("com.tterrag.registrate:Registrate:${property("registrate_version")}")

    compileOnly("cc.tweaked:cc-tweaked-${rootProject.property("minecraft_version")}-core:${rootProject.property("cct_version")}")
    compileOnly("cc.tweaked:cc-tweaked-${rootProject.property("minecraft_version")}-forge:${rootProject.property("cct_version")}")
    compileOnly("cc.tweaked:cc-tweaked-${rootProject.property("minecraft_version")}-forge-api:${rootProject.property("cct_version")}")

    add("shadowIn", "dev.arbjerg:lavaplayer:${rootProject.property("lava_version_youtube")}") {
        exclude(group = "dev.arbjerg", module = "lavaplayer-natives")
    }
    add("shadowIn", "com.github.sealedtx:java-youtube-downloader:${rootProject.property("ytdownloader")}")
    add("shadowIn", "dev.felnull:felnull-java-library:${rootProject.property("felnull_version")}")
    add("shadowIn", "com.mpatric:mp3agic:0.9.1")
    add("shadowIn", "dev.lavalink.youtube:v2:${rootProject.property("lavalink")}")

    add("forgeRuntimeLibrary", "dev.felnull:felnull-java-library:${rootProject.property("felnull_version")}")
    add("forgeRuntimeLibrary", "dev.arbjerg:lavaplayer:${rootProject.property("lava_version_youtube")}") {
        exclude(group = "dev.arbjerg", module = "lavaplayer-natives")
    }
    add("forgeRuntimeLibrary", "com.github.sealedtx:java-youtube-downloader:${rootProject.property("ytdownloader")}")
    add("forgeRuntimeLibrary", "com.mpatric:mp3agic:0.9.1")
    add("forgeRuntimeLibrary", "dev.lavalink.youtube:v2:${rootProject.property("lavalink")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.withType(ShadowJar::class.java).named("shadowJar") {
    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    configurations = listOf(project.configurations["shadowCommon"])
    archiveClassifier.set("dev-shadow")

    mergeServiceFiles {
        relocate("javax.ws.rs.ext", "dev.felnull.imp.include.javax.ws.rs.ext")
        relocate("org.glassfish.jersey.internal.spi", "dev.felnull.imp.include.org.glassfish.jersey.internal.spi")
    }
    relocate("org.json", "dev.felnull.imp.include.org.json")
    relocate("org.slf4j", "dev.felnull.imp.include.org.slf4j")
    relocate("certificates", "dev.felnull.imp.include.certificates")
    relocate("com.fasterxml", "dev.felnull.imp.include.com.fasterxml")
    relocate("natives", "dev.felnull.imp.include.natives")
    relocate("mozilla", "dev.felnull.imp.include.mozilla")
    relocate("net.iharder", "dev.felnull.imp.include.net.iharder")
    relocate("com.sedmelluq.lava", "dev.felnull.imp.include.com.sedmelluq.lava")
    relocate("org.apache.http", "dev.felnull.imp.include.org.apache.http")
    relocate("org.apache.commons", "dev.felnull.imp.include.org.apache.commons") {
        include("org.apache.commons.logging.**")
        include("org.apache.commons.io.**")
        include("org.apache.commons.codec.**")
    }
    relocate("com.github", "dev.felnull.imp.include.com.github") {
        exclude("com.github.tartaricacid.**")
    }
    relocate("com.alibaba", "dev.felnull.imp.include.com.alibaba")
    relocate("dev.felnull.fnjl", "dev.felnull.imp.include.dev.felnull.fnjl")
    relocate("com.mpatric", "dev.felnull.imp.include.com.mpatric")
    relocate("com.sedmelluq.discord.lavaplayer", "dev.felnull.imp.include.com.sedmelluq.discord.lavaplayer") {
        exclude("com.sedmelluq.discord.lavaplayer.natives.**")
    }

    relocate("ibxm", "dev.felnull.imp.include.ibxm")
    relocate("net.sourceforge.jaad", "dev.felnull.imp.include.net.sourceforge.jaad")
    relocate("org.mozilla.javascript", "dev.felnull.imp.include.org.mozilla.javascript")
    relocate("org.mozilla.classfile", "dev.felnull.imp.include.org.mozilla.classfile")
    relocate("dev.lavalink.youtube", "dev.felnull.imp.include.dev.lavalink.youtube")
    relocate("org.jsoup", "dev.felnull.imp.include.org.jsoup")
}

tasks.withType(net.fabricmc.loom.task.RemapJarTask::class.java).named("remapJar") {
    input.set(tasks.withType(ShadowJar::class.java).named("shadowJar").get().archiveFile)
    dependsOn(tasks.named("shadowJar"))
    archiveClassifier.set(null)
}

tasks.named("jar", Jar::class.java) {
    archiveClassifier.set("dev")
}

tasks.named("sourcesJar", Jar::class.java) {
    dependsOn(project(":common").tasks.named("sourcesJar", Jar::class.java))
    from(zipTree(project(":common").tasks.named("sourcesJar", Jar::class.java).get().archiveFile))
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}
