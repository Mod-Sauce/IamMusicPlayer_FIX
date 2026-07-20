import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "9.4.1"
}

architectury {
    platformSetupLoomIde()
    fabric()
}

sourceSets {
    main {
        resources {
            srcDirs("src/main/generated")
        }
    }
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath

    mixin {
        defaultRefmapName = "iammusicplayer-common.refmap.json"
    }

    runs {
        register("datagen") {
            server()

            name("Minecraft Data")
            vmArg("-Dfabric-api.datagen")
            vmArg("-Dfabric-api.datagen.output-dir=${file("src/main/generated")}")

            runDir("run")
        }
    }
}

configurations.create("common")
configurations.create("shadowCommon")
configurations.create("shadowIn")

configurations.compileClasspath.get().extendsFrom(configurations.getByName("common"))
configurations.runtimeClasspath.get().extendsFrom(configurations.getByName("common"))
configurations.named("developmentFabric") { extendsFrom(configurations.getByName("common")) }
configurations.implementation.get().extendsFrom(configurations.getByName("shadowIn"))
configurations.getByName("shadowCommon").extendsFrom(configurations.getByName("shadowIn"))

repositories {
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://cursemaven.com")
    maven("https://maven.ladysnake.org/releases")
    mavenCentral()
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    modApi("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")

    modApi("dev.architectury:architectury-fabric:${rootProject.property("architectury_version")}")

    add("common", project(":common", configuration = "namedElements")) { isTransitive = false }
    add("shadowCommon", project(":common", configuration = "transformProductionFabric")) { isTransitive = false }

    val localJar = file("../../OtyacraftEngineRenewed/fabric/build/libs/otyacraftenginerenewed-fabric-mc1.21.1-${rootProject.property("oe_version")}.jar")
    if (localJar.exists()) {
        modApi(files(localJar))
    } else {
        modApi("org.mod-sauce:otyacraftenginerenewed-fabric:${rootProject.property("oe_version")}")
    }

    modApi("com.terraformersmc:modmenu:${rootProject.property("modmenu_version")}")
    modApi("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_config_version")}")
    modRuntimeOnly("me.shedaniel:RoughlyEnoughItems-fabric:${rootProject.property("rei_version")}")

    modCompileOnly("curse.maven:touhoulittlemaid-orihime-1311287:7549503")
    modApi("vazkii.patchouli:Patchouli:${rootProject.property("patchouli_version")}-FABRIC")
    modCompileOnly("org.ladysnake.cardinal-components-api:cardinal-components-base:${rootProject.property("cca_version")}")
    modCompileOnly("org.ladysnake.cardinal-components-api:cardinal-components-entity:${rootProject.property("cca_version")}")

    modCompileOnly("cc.tweaked:cc-tweaked-${rootProject.property("minecraft_version")}-fabric-api:${rootProject.property("cct_version")}")
    modCompileOnly("cc.tweaked:cc-tweaked-${rootProject.property("minecraft_version")}-fabric:${rootProject.property("cct_version")}")

    modApi("dev.felnull:special-model-loader:1.3.0")
    include("dev.felnull:special-model-loader:1.3.0")
    modApi("maven.modrinth:sound-physics-remastered:fabric-${rootProject.property("sound_physics_remastered")}")

    add("shadowIn", "dev.arbjerg:lavaplayer:${rootProject.property("lava_version_youtube")}") {
        exclude(group = "dev.arbjerg", module = "lavaplayer-natives")
    }
    add("shadowIn", "dev.lavalink.youtube:v2:${rootProject.property("lavalink")}")

    add("shadowIn", "com.github.sealedtx:java-youtube-downloader:${rootProject.property("ytdownloader")}")
    add("shadowIn", "dev.felnull:felnull-java-library:${rootProject.property("felnull_version")}")
    add("shadowIn", "com.mpatric:mp3agic:0.9.1")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType(ShadowJar::class.java).named("shadowJar") {
    configurations = listOf(project.configurations["shadowIn"])
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

    mergeServiceFiles {
        relocate("javax.ws.rs.ext", "dev.felnull.imp.include.javax.ws.rs.ext")
        relocate("org.glassfish.jersey.internal.spi", "dev.felnull.imp.include.org.glassfish.jersey.internal.spi")
    }

    relocate("ibxm", "dev.felnull.imp.include.ibxm")
    relocate("net.sourceforge.jaad", "dev.felnull.imp.include.net.sourceforge.jaad")
    relocate("org.mozilla.javascript", "dev.felnull.imp.include.org.mozilla.javascript")
    relocate("org.mozilla.classfile", "dev.felnull.imp.include.org.mozilla.classfile")
    relocate("dev.lavalink.youtube", "dev.felnull.imp.include.dev.lavalink.youtube")
    relocate("org.jsoup", "dev.felnull.imp.include.org.jsoup")
}

tasks.withType(ShadowJar::class.java).named("shadowJar") {
    exclude("architectury.common.json")

    configurations = listOf(project.configurations["shadowCommon"])
    archiveClassifier.set("dev-shadow")
    from("build/resources/main") {
        include("**/*.refmap.json")
        include("**/*.mixins.json")
    }
}

tasks.withType(net.fabricmc.loom.task.RemapJarTask::class.java).named("remapJar") {
    injectAccessWidener = true
    input.set(tasks.withType(ShadowJar::class.java).named("shadowJar").get().archiveFile)
    dependsOn(tasks.named("shadowJar"))
    archiveClassifier.set(null)
}

tasks.named("jar", Jar::class.java) {
    archiveClassifier.set("dev")
}

tasks.named("sourcesJar", Jar::class.java) {
    val commonSources = project(":common").tasks.named("sourcesJar", Jar::class.java)
    dependsOn(commonSources)
    from(commonSources.map { zipTree(it.archiveFile) })
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
    skip()
}

tasks.register<Delete>("cleanGenerated") {
    delete(file("src/main/generated"))
}

tasks.named("runDatagen") {
    dependsOn(tasks.named("cleanGenerated"))
}
