dependencies {
    api(project(":decorate-api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.hikaricp)

    testImplementation(libs.paper.api)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.hikaricp)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
