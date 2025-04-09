import io.freefair.gradle.plugins.mjml.ValidationMode
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

group = "fi.hel.haitaton"

version = "0.0.1-SNAPSHOT"

val sentryVersion = "8.7.0"
val geoToolsVersion = "32.2"

repositories {
    mavenCentral().content { excludeModule("javax.media", "jai_core") }
    maven { url = uri("https://repo.osgeo.org/repository/release/") }
    maven { url = uri("https://maven.geotoolkit.org") }
}

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
    environment("HAITATON_BLOB_CONNECTION_STRING", "UseDevelopmentStorage=true;")
    environment("HAITATON_GDPR_DISABLED", "true")
}

spotless {
    ratchetFrom("origin/dev") // only format files which have changed since origin/dev

    kotlin {
        ktfmt("0.53").kotlinlangStyle()
        toggleOffOn()
    }
}

plugins {
    val kotlinVersion = "2.1.20"
    id("org.springframework.boot") version "3.3.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.3"
    kotlin("jvm") version kotlinVersion
    // Gives kotlin-allopen, which auto-opens classes with certain annotations
    kotlin("plugin.spring") version kotlinVersion
    // Gives kotlin-noarg for @Entity, @Embeddable
    kotlin("plugin.jpa") version kotlinVersion
    idea
    id("jacoco")
    id("io.freefair.mjml.java") version "8.13.1"
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
    implementation("ch.qos.logback.access:logback-access-tomcat:2.0.6")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("de.grundid.opendatalab:geojson-jackson:1.14")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.liquibase:liquibase-core")
    implementation("com.github.blagerweij:liquibase-sessionlock:1.6.9")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.2")
    implementation("commons-io:commons-io:2.18.0")
    implementation("net.pwall.mustache:kotlin-mustache:0.12")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.auth0:java-jwt:4.5.0")

    implementation("org.postgresql:postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.28.1")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation("com.icegreen:greenmail-junit5:2.1.3")

    // Pdf generation
    implementation("com.github.librepdf:openpdf:2.0.3")
    implementation("org.apache.xmlgraphics:fop:2.10")

    // Geotools
    implementation("org.geotools:gt-wms:$geoToolsVersion")
    implementation("org.geotools:gt-brewer:$geoToolsVersion")
    implementation("org.geotools:gt-epsg-hsql:$geoToolsVersion")
    implementation("org.locationtech.jts.io:jts-io-common:1.20.0")

    // Testcontainers
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
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

    // Azure
    implementation(platform("com.azure:azure-sdk-bom:1.2.33"))
    implementation("com.azure:azure-storage-blob")
    implementation("com.azure:azure-storage-blob-batch")
    implementation("com.azure:azure-identity")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

tasks.withType<KotlinCompile> { compilerOptions { freeCompilerArgs = listOf("-Xjsr305=strict") } }

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
        shouldRunAfter("spotlessCheck")
        outputs.upToDateWhen { false }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    create("copyEmailTemplates", Copy::class) {
        group = "other"
        description = "Installs shared git hooks"
        from(file("${layout.buildDirectory.get()}/mjml/main/"))
        into(file("${sourceSets.main.get().resources.srcDirs.first()}/email/template"))
        rename { "$it.mustache" }
        dependsOn(compileMjml)
    }

    jacocoTestReport {
        mustRunAfter("test", "integrationTest")
        reports { xml.required.set(true) }
        executionData.setFrom(
            fileTree(layout.buildDirectory)
                .include("/jacoco/test.exec", "/jacoco/integrationTest.exec")
        )
    }

    compileMjml { source(file("$rootDir/email")) }
}

tasks.register("installGitHook", Copy::class) {
    group = "other"
    description = "Installs shared git hooks"
    from(file("$rootDir/githooks"))
    into(file("$rootDir/.git/hooks"))
    fileMode = 0b0111101101 // -rwxr-xr-x
}

tasks.named("build") { dependsOn(tasks.named("installGitHook")) }

tasks.named("check") { dependsOn(tasks.named("integrationTest")) }

tasks.named("processResources") { dependsOn(tasks.named("copyEmailTemplates")) }

mjml {
    // For explanations on the configuration values, see
    // https://github.com/mjmlio/mjml/blob/master/packages/mjml-cli/README.md
    validationMode.set(ValidationMode.strict)
    minify.set(true)
    beautify.set(true)

    // For minifier options, see https://github.com/kangax/html-minifier
    minifyOptions.set(
        """{"collapseWhitespace": false, "minifyCSS": true, "removeEmptyAttributes": true }"""
    )
}

node { download.set(true) }
