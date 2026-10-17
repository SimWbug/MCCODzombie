plugins {
    java
}

group = "fr.cleangang"
version = providers.gradleProperty("pluginVersion").get()

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val pluginApiVersion = providers.gradleProperty("pluginApiVersion").get()
val javaVersion = providers.gradleProperty("javaVersion").get().toInt()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    val props = mapOf("version" to project.version, "apiVersion" to pluginApiVersion)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveFileName.set("CleanZombies-${project.version}.jar")
}

// Construit le pack de ressources (zip) à côté du jar : build/resourcepack/CleanZombies-pack.zip
val packZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Assemble le pack de ressources du mode Zombies."
    from(layout.projectDirectory.dir("resourcepack"))
    archiveFileName.set("CleanZombies-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("resourcepack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.build { dependsOn(packZip) }
