architectury {
    common(rootProject.property("enabled_platforms").toString().split(","))
}

loom {
    accessWidenerPath = file("src/main/resources/iammusicplayer.accesswidener")
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")

    modApi("dev.architectury:architectury:${rootProject.property("architectury_version")}")

    val localJar = file("../../OtyacraftEngineRenewed/common/build/libs/otyacraftenginerenewed-${rootProject.property("oe_version")}.jar")
    if (localJar.exists()) {
        modApi(files(localJar))
    } else {
        modApi("org.mod-sauce:otyacraftenginerenewed:${rootProject.property("oe_version")}")
    }

    modApi("me.shedaniel.cloth:cloth-config:${rootProject.property("cloth_config_version")}")

    implementation("dev.felnull:felnull-java-library:${rootProject.property("felnull_version")}")

    implementation("dev.arbjerg:lavaplayer:${rootProject.property("lava_version_youtube")}")
    implementation("dev.lavalink.youtube:v2:${rootProject.property("lavalink")}")

    implementation("com.github.sealedtx:java-youtube-downloader:${rootProject.property("ytdownloader")}")
    implementation("com.mpatric:mp3agic:0.9.1")

    compileOnly("cc.tweaked:cc-tweaked-${rootProject.property("minecraft_version")}-common-api:${rootProject.property("cct_version")}")

    api("dev.ryanhcode.sable-companion:sable-companion-common-${project.property("minecraft_version")}:${project.property("sable_companion_version")}")
}
