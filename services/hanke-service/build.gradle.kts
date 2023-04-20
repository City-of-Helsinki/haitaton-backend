import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

group = "fi.hel.haitaton"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11
val springDocVersion = "1.6.12"
val geoJsonJacksonVersion = "1.14"
val mockkVersion = "1.13.5"
val springmockkVersion = "3.1.2"
val assertkVersion = "0.25"

repositories {
	mavenCentral()
}

sourceSets {
	create("integrationTest") {
		compileClasspath += main.get().output + test.get().output
		runtimeClasspath += main.get().output + test.get().output
	}
}

val integrationTestImplementation: Configuration by configurations.getting {
	extendsFrom(configurations.testImplementation.get())
}

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

idea {
	module {
		testSourceDirs = testSourceDirs + sourceSets["integrationTest"].withConvention(KotlinSourceSet::class) { kotlin.srcDirs }
		testResourceDirs = testResourceDirs + sourceSets["integrationTest"].resources.srcDirs
	}
}

springBoot {
	buildInfo()
}

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
	id("org.springframework.boot") version "2.7.11"
	id("io.spring.dependency-management") version "1.1.0"
	id("com.diffplug.spotless") version "6.10.0"
	kotlin("jvm") version "1.6.21"
	// Gives kotlin-allopen, which auto-opens classes with certain annotations
	kotlin("plugin.spring") version "1.6.21"
	// Gives kotlin-noarg for @Entity, @Embeddable
	kotlin("plugin.jpa") version "1.6.21"
	idea
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
	implementation("net.logstash.logback:logstash-logback-encoder:6.5")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("de.grundid.opendatalab:geojson-jackson:$geoJsonJacksonVersion")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.liquibase:liquibase-core")
	implementation("com.github.blagerweij:liquibase-sessionlock:1.6.2")
	implementation("com.vladmihalcea:hibernate-types-52:2.14.0")
	implementation("commons-io:commons-io:2.11.0")
	implementation("com.github.librepdf:openpdf:1.3.30")
	implementation("net.pwall.mustache:kotlin-mustache:0.10")

	implementation("org.postgresql:postgresql")
	implementation("org.springdoc:springdoc-openapi-kotlin:$springDocVersion")
	implementation("org.springdoc:springdoc-openapi-ui:$springDocVersion")

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation("io.mockk:mockk:$mockkVersion")
	testImplementation("com.ninja-squad:springmockk:$springmockkVersion")
	testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
	testImplementation("com.squareup.okhttp3:okhttp:4.9.3")
	testImplementation("com.squareup.okhttp3:mockwebserver:4.9.3")
	testImplementation("com.icegreen:greenmail-junit5:1.6.14")

	// Testcontainers
	implementation(platform("org.testcontainers:testcontainers-bom:1.18.0"))
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")

	// Spring Boot Management
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	testImplementation("org.springframework.security:spring-security-test")
	// Sentry
	implementation("io.sentry:sentry-spring-boot-starter:4.0.0")
	implementation("io.sentry:sentry-logback:4.0.0")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

tasks {
	test {
		useJUnitPlatform()
		systemProperty("spring.profiles.active", "test")
	}

	create("integrationTest", Test::class) {
		useJUnitPlatform()
		group = "verification"
		systemProperty("spring.profiles.active", "integrationTest")
		testClassesDirs = sourceSets["integrationTest"].output.classesDirs
		classpath = sourceSets["integrationTest"].runtimeClasspath
		shouldRunAfter("test")
		outputs.upToDateWhen { false }
	}
}

tasks.register("installGitHook", Copy::class) {
	from(file("$rootDir/githooks"))
	into(file("$rootDir/.git/hooks"))
	fileMode = 0b0111101101 // -rwxr-xr-x
}
tasks.named("build") {
	dependsOn(tasks.named("installGitHook"))
}
