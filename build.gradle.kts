plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    runServer {
        minecraftVersion("1.21.4")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    withType<JavaCompile>().configureEach {
        options.release.set(21)
    }

    processResources {
        val props = mapOf(
            "version" to version,
            "author" to "Teighto"
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
        filesMatching("config.yml") {
            expand(props)
        }
    }
}
