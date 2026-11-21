dependencies {
    api(project(":decorate-api"))
    compileOnly(libs.paper.api)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
