import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "fi.hel.haitaton"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11
val springDocVersion = "1.4.8"
val geoJsonJacksonVersion = "1.14"
val mockkVersion = "1.10.2"
val springmockkVersion = "2.0.3"
val assertkVersion = "0.23"

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
		testSourceDirs =
				testSourceDirs + sourceSets["integrationTest"].withConvention(KotlinSourceSet::class) { kotlin.srcDirs }
		testResourceDirs = testResourceDirs + sourceSets["integrationTest"].resources.srcDirs
	}
}

springBoot {
	buildInfo()
}

plugins {
	id("org.springframework.boot") version "2.3.4.RELEASE"
	id("io.spring.dependency-management") version "1.0.10.RELEASE"
	kotlin("jvm") version "1.4.20"
	// Gives kotlin-allopen, which auto-opens classes with certain annotations
	kotlin("plugin.spring") version "1.4.20"
	// Gives kotlin-noarg for @Entity, @Embeddable
	kotlin("plugin.jpa") version "1.4.20"
	idea
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("io.github.microutils:kotlin-logging:1.12.0")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("de.grundid.opendatalab:geojson-jackson:$geoJsonJacksonVersion")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.liquibase:liquibase-core")								// TODO: do we need org.springframework:spring-dao ?

	runtimeOnly("org.postgresql:postgresql")
	// H2 is used as embedded db for some simple low level Entity and Repository class testing
	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.springdoc:springdoc-openapi-ui:$springDocVersion")

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation("io.mockk:mockk:$mockkVersion")
	testImplementation("com.ninja-squad:springmockk:$springmockkVersion")
	testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
	testImplementation("org.testcontainers:junit-jupiter:1.15.0")
	testImplementation("org.testcontainers:postgresql:1.15.0")
	// Spring Boot Management
	implementation("org.springframework.boot:spring-boot-starter-actuator")
}

//tasks.withType<Test> {
//	useJUnitPlatform()
//}

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
