dependencies {
    api(project(":decorate-api"))
    implementation(project(":decorate-core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.worldguard.bukkit)

    testImplementation(libs.paper.api)
    testImplementation(libs.placeholderapi)
    testImplementation(libs.worldguard.bukkit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
