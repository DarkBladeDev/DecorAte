plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":decorate-api"))
    implementation(project(":decorate-core"))
    implementation(project(":decorate-packet"))
    implementation(project(":decorate-hooks"))

    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents.spigot)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.worldguard.bukkit)

    implementation(libs.cloud.paper)
    implementation(libs.cloud.annotations)
    implementation(libs.libby.bukkit)
    annotationProcessor(libs.cloud.annotations)

    testImplementation(libs.paper.api)
    testImplementation(libs.packetevents.spigot)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
    filesMatching("runtime-libraries.properties") {
        expand(
            "hikariVersion" to libs.versions.hikari.get(),
            "sqliteVersion" to libs.versions.sqlite.get(),
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("DecorAte")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    relocate("org.incendo.cloud", "dev.decorate.libs.cloud")
    relocate("net.byteflux.libby", "dev.decorate.libs.libby")
    relocate("com.zaxxer.hikari", "dev.decorate.libs.hikari")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
