import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.ktlint)
}

group = "no.novari"

private val fintLabsRepo = uri("https://repo.fintlabs.no/releases")

repositories {
    mavenCentral()
    maven {
        url = fintLabsRepo
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

// Build with JDK 25, publish for Java 21.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjdk-release=21")
        // Spring Boot 4.1 manages Kotlin 2.3, so apps may run with a 2.3 stdlib.
        apiVersion = KotlinVersion.KOTLIN_2_3
    }
    // The stdlib version Spring Boot manages, not the Kotlin plugin's, so the library does not upgrade it in apps.
    coreLibrariesVersion =
        libs.versions.kotlin.stdlib
            .get()
}

ktlint {
    version = libs.versions.ktlint.cli
}

dependencyLocking {
    lockAllConfigurations()
}

// The Spring Boot BOM sets versions in this build only. It is not published, so it cannot change the app's Spring Boot
// or Kotlin version. versionMapping below publishes the resolved versions instead.
val springBootBom = configurations.dependencyScope("springBootBom")
listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach {
    configurations.named(it) { extendsFrom(springBootBom.get()) }
}

dependencies {
    springBootBom(platform(libs.spring.boot.dependencies))

    constraints {
        ktlint(libs.logback.core) {
            because(
                "ktlint 1.8.0 brings logback-core 1.3.16, which has CVE-2026-1225, CVE-2026-9828 and " +
                    "CVE-2026-10532. Only the ktlint tool classpath, not the published library",
            )
        }
    }

    api(libs.flyt.kafka)
    api(libs.flyt.security.starter)

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.apache.httpcomponents.client5:httpclient5")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            url = fintLabsRepo
            credentials {
                username = System.getenv("REPOSILITE_USERNAME")
                password = System.getenv("REPOSILITE_PASSWORD")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // Publish the versions resolved from the Spring Boot BOM, so consumers do not need the BOM.
            versionMapping {
                allVariants {
                    fromResolutionResult()
                }
            }
        }
    }
}
