import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

group = "fi.hel.haitaton"

version = "0.0.1-SNAPSHOT"

val sentryVersion = "6.23.0"

ext["spring-security.version"] = "6.0.4"

repositories { mavenCentral() }

sourceSets {
    create("integrationTest") {
        compileClasspath += main.get().output + test.get().output
        runtimeClasspath += main.get().output + test.get().output
    }
}

val integrationTestImplementation: Configuration by
    configurations.getting { extendsFrom(configurations.testImplementation.get()) }

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

idea {
    module {
        testSources.from(kotlin.sourceSets["integrationTest"].kotlin.srcDirs)
        testResources.from(kotlin.sourceSets["integrationTest"].resources.srcDirs)
    }
}

springBoot { buildInfo() }

tasks.getByName<BootRun>("bootRun") {
    environment("HAITATON_SWAGGER_PATH_PREFIX", "/v3")
    environment("HAITATON_EMAIL_ENABLED", "true")
}

spotless {
    ratchetFrom("origin/dev") // only format files which have changed since origin/dev

    kotlin {
        ktfmt("0.39").kotlinlangStyle()
        toggleOffOn()
    }
}

plugins {
    id("org.springframework.boot") version "3.0.8"
    id("io.spring.dependency-management") version "1.1.3"
    id("com.diffplug.spotless") version "6.21.0"
    kotlin("jvm") version "1.8.22"
    // Gives kotlin-allopen, which auto-opens classes with certain annotations
    kotlin("plugin.spring") version "1.8.22"
    // Gives kotlin-noarg for @Entity, @Embeddable
    kotlin("plugin.jpa") version "1.8.22"
    idea
    id("com.github.ben-manes.versions") version "0.42.0"
    id("jacoco")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.integration:spring-integration-jdbc")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("ch.qos.logback:logback-access")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("de.grundid.opendatalab:geojson-jackson:1.14")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.liquibase:liquibase-core")
    implementation("com.github.blagerweij:liquibase-sessionlock:1.6.5")
    implementation("io.hypersistence:hypersistence-utils-hibernate-60:3.5.0")
    implementation("commons-io:commons-io:2.13.0")
    implementation("com.github.librepdf:openpdf:1.3.30")
    implementation("net.pwall.mustache:kotlin-mustache:0.11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("org.postgresql:postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.26.1")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation("com.icegreen:greenmail-junit5:2.0.0")

    // Testcontainers
    implementation(platform("org.testcontainers:testcontainers-bom:1.18.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // Spring Boot Management
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-test")

    // Sentry
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")
    implementation("io.sentry:sentry-logback:$sentryVersion")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

tasks.withType<KotlinCompile> { kotlinOptions { freeCompilerArgs = listOf("-Xjsr305=strict") } }

kotlin { jvmToolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

tasks {
    test {
        useJUnitPlatform()
        systemProperty("spring.profiles.active", "test")
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    create("integrationTest", Test::class) {
        useJUnitPlatform()
        group = "verification"
        systemProperty("spring.profiles.active", "integrationTest")
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        shouldRunAfter("test")
        outputs.upToDateWhen { false }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    jacocoTestReport {
        mustRunAfter("test", "integrationTest")
        reports { xml.required.set(true) }
        executionData.setFrom(
            fileTree(buildDir).include("/jacoco/test.exec", "/jacoco/integrationTest.exec")
        )
    }
}

tasks.register("installGitHook", Copy::class) {
    from(file("$rootDir/githooks"))
    into(file("$rootDir/.git/hooks"))
    fileMode = 0b0111101101 // -rwxr-xr-x
}

tasks.named("build") { dependsOn(tasks.named("installGitHook")) }
