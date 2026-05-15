plugins {
	java
	`maven-publish`
	id("io.freefair.lombok") version "+"
}

group = "org.mtr"
version = File(rootDir.parentFile, "gradle.properties").readLines().find { it.startsWith("version=") }?.substringAfter("=") ?: "unspecified"

repositories {
	mavenCentral()
}

dependencies {
	implementation(gradleApi())
	implementation("com.google.code.gson:gson:+")
	implementation("it.unimi.dsi:fastutil:+")
	implementation("commons-io:commons-io:+")
	implementation("log4j:log4j:+")
	implementation("org.jspecify:jspecify:+")
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
	withSourcesJar()
	withJavadocJar()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}

	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "owner/repo"}")
			credentials {
				username = System.getenv("GITHUB_ACTOR")
				password = System.getenv("GITHUB_TOKEN")
			}
		}
	}
}

tasks.withType<AbstractArchiveTask> {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
	exclude("website/**")
}
