plugins {
    id("java")
}

group = "dev.joaquim"
version = "1.0.0"

// ---------------------------------------------------------------------------
// Paper 26.x usa o novo esquema de versao (ano.drop), e o artefato segue o
// formato "<versao>.build.<n>-stable" — nao existe mais "-R0.1-SNAPSHOT".
//
// Para achar o build mais novo da sua versao, abra:
//   https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/
// Se preferir sempre o ultimo build, troque a linha por:
//   val paperApiVersion = "[26.2.build,)"
// ---------------------------------------------------------------------------
val minecraftVersion = "26.2"
val paperApiVersion = "26.2.build.123-stable"

// Minecraft 26.1+ exige Java 25.
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to minecraftVersion
    )
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("RemoteChests")
    archiveClassifier.set("")
}
