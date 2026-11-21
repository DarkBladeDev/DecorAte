dependencies {
    api(project(":decorate-api"))
    implementation(project(":decorate-core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents.spigot)

    testImplementation(libs.paper.api)
    testImplementation(libs.packetevents.spigot)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
