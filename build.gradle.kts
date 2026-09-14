plugins {
    id("java")
}

group = "dev.joaquim"
version = "1.0.0"

// ---------------------------------------------------------------------------
// Ajuste estes dois valores se a sua versao do Paper for diferente.
// O artefato do Paper segue o padrao "<versao>-R0.1-SNAPSHOT".
// ---------------------------------------------------------------------------
val minecraftVersion = "26.2"
val paperApiVersion = "$minecraftVersion-R0.1-SNAPSHOT"
val javaVersion = 21

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
