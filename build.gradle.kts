plugins {
    java
}

allprojects {
    group = "dev.decorate"
    version = "0.2.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        maven("https://repo.codemc.io/repository/maven-releases/") {
            name = "codemc-releases"
        }
        maven("https://repo.extendedclip.com/releases/") {
            name = "placeholderapi"
        }
        maven("https://maven.enginehub.org/repo/") {
            name = "enginehub"
        }
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
