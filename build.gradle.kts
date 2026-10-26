plugins {
    java
    id("com.gradleup.shadow") version "9.3.0"
}

group = "me.TreeOfSelf"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("org.geysermc.geyser:api:2.12.0-SNAPSHOT")
    compileOnly("org.geysermc.geyser:core:2.12.0-SNAPSHOT") { isTransitive = false }
    compileOnly("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta12-SNAPSHOT")
    compileOnly("net.kyori:adventure-key:4.17.0")
    compileOnly("net.kyori:adventure-api:4.17.0")
    // the full fastutil ships with Minecraft
    compileOnly("it.unimi.dsi:fastutil:8.5.15")
    compileOnly("org.geysermc.mcprotocollib:protocol:1.21.11-20260217.182735-13") { isTransitive = false }
}

// the Bedrock movement engine
sourceSets.main { java.srcDir("engine/src") }

tasks.shadowJar {
    archiveFileName = "PandaGeyserAC.jar"
    // Geyser-Fabric shades these two
    relocate("org.cloudburstmc.protocol", "org.geysermc.geyser.shaded.org.cloudburstmc.protocol")
    relocate("org.cloudburstmc.netty", "org.geysermc.geyser.shaded.org.cloudburstmc.netty")
}
tasks.build { dependsOn(tasks.shadowJar) }
