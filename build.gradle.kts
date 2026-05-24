import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import org.apache.tools.ant.filters.ReplaceTokens
import org.mtr.core.Generator
import org.mtr.core.WebserverSetup

plugins {
	java
	`maven-publish`
	id("io.freefair.lombok") version "+"
	id("com.gradleup.shadow") version "+"
}

group = "org.mtr"
version = project.version

repositories {
	mavenCentral()
	maven(url = "https://repo.mikeprimm.com/")
	flatDir { dirs("libs") }
}

dependencies {
	// Core
	implementation("com.google.code.gson:gson:+")
	implementation("it.unimi.dsi:fastutil:+")
	implementation("commons-io:commons-io:+")
	implementation("com.squareup.okhttp3:okhttp:+")
	implementation("org.eclipse.jetty:jetty-servlet:+")
	implementation("org.msgpack:msgpack-core:+")
	implementation("org.apache.logging.log4j:log4j-core:2.+")
	implementation("org.jspecify:jspecify:+")
	implementation("info.picocli:picocli:+")
	runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.+")

	// Compile-only
	compileOnly("xyz.jpenilla:squaremap-api:+")
	compileOnly("net.kyori:adventure-api:+")
	compileOnly("us.dynmap:DynmapCoreAPI:3.8")

	// Test
	testImplementation("org.junit.jupiter:junit-jupiter-api:+")
	testImplementation("org.junit.platform:junit-platform-launcher:+")
	testImplementation("org.apache.httpcomponents:httpclient:+")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:+")
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
			artifact(tasks.shadowJar)
			artifact(tasks.named("sourcesJar"))
			artifact(tasks.named("javadocJar"))
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

tasks.jar {
	manifest {
		attributes("Main-Class" to "org.mtr.core.Main")
	}
}

tasks.shadowJar {
	archiveClassifier.set("")
	mergeServiceFiles()
	transform(Log4j2PluginsCacheFileTransformer())
	minimize {
		exclude(dependency("com.google.code.gson:gson"))
		exclude(dependency("com.squareup.okhttp3:okhttp"))
		exclude(dependency("it.unimi.dsi:fastutil"))
		exclude(dependency("org.eclipse.jetty:jetty-servlet"))
		exclude(dependency("org.msgpack:msgpack-core"))
	}
	relocate("com", "org.mtr.libraries.com") { skipStringConstants = true }
	relocate("it", "org.mtr.libraries.it") { skipStringConstants = true }
	relocate("jakarta", "org.mtr.libraries.jakarta")
	relocate("kotlin", "org.mtr.libraries.kotlin") { skipStringConstants = true }
	relocate("okhttp3", "org.mtr.libraries.okhttp3") { skipStringConstants = true }
	relocate("okio", "org.mtr.libraries.okio") { skipStringConstants = true }
	relocate("org", "org.mtr.libraries.org") { skipStringConstants = true; exclude("org.mtr.**") }
	relocate("picocli", "org.mtr.libraries.picocli") { skipStringConstants = true }
}

tasks.withType<AbstractArchiveTask> {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
}

tasks.test {
	useJUnitPlatform()
	testLogging { showStandardStreams = true }
}

tasks.javadoc {
	// Suppress "missing" doclint only (generated classes don't need javadoc)
	(options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

val generateVersionTask = tasks.register("generateVersion") {
	outputs.file("website/src/version.ts")
	outputs.file("src/main/java/org/mtr/core/Version.java")

	doLast {
		val tokens = mapOf("version" to version)
		copy {
			from("website/version-template.txt")
			into("website/src")
			filter(mapOf("tokens" to tokens), ReplaceTokens::class.java)
			rename { "version.ts" }
		}
		copy {
			from("src/main/VersionTemplate.java")
			into("src/main/java/org/mtr/core")
			filter(mapOf("tokens" to tokens), ReplaceTokens::class.java)
			rename { "Version.java" }
		}
	}
}

val generateSchemaClassesTask = tasks.register("generateSchemaClasses") {
	inputs.dir("buildSrc/src/main/resources/schema")
	outputs.dir("src/main/java/org/mtr/core/generated")
	outputs.dir("src/main/java/org/mtr/legacy/generated")
	outputs.dir("website/src/app/entity/generated")

	doLast {
		Generator.generateJava(project, "schema/data", "core/generated/data", true, "core.data", "core.simulation")
		Generator.generateJava(project, "schema/legacy", "legacy/generated/data", false, "core.data", "legacy.data")
		Generator.generateJava(project, "schema/integration", "core/generated/integration", false, "core.data", "core.integration")
		Generator.generateJava(project, "schema/map", "core/generated/map", false, "core.map")
		Generator.generateJava(project, "schema/oba", "core/generated/oba", false, "core.oba")
		Generator.generateJava(project, "schema/operation", "core/generated/operation", false, "core.data", "core.operation")
		Generator.generateTypeScript(project, "schema/map", "website/src/app/entity/generated")
	}
}

val setupWebserverTask = tasks.register("setupWebserver") {
	outputs.file("src/main/java/org/mtr/core/generated/WebserverResources.java")

	doLast {
		WebserverSetup.setup(project.rootDir, "", "core")
	}
}

tasks.withType<JavaCompile> {
	// CODE_STYLES.md §3.13: enable all linting with specific suppressions
	options.compilerArgs.addAll(
		listOf(
			"-Xlint:all",
			"-Xlint:-serial",     // No Java serialization
			"-Xlint:-processing", // Lombok annotation processor noise
			"-Xlint:-this-escape" // Safe: schema constructor pattern
		)
	)

	dependsOn(generateVersionTask, generateSchemaClassesTask, setupWebserverTask)
}

tasks.named<Jar>("sourcesJar") {
	dependsOn(generateVersionTask, generateSchemaClassesTask, setupWebserverTask)
}
