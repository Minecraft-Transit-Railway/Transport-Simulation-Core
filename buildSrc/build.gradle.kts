repositories {
	mavenCentral()
}

dependencies {
	implementation("com.google.code.gson:gson:+")
	implementation("it.unimi.dsi:fastutil:+")
	implementation("commons-io:commons-io:+")
	implementation("org.jspecify:jspecify:+")

	compileOnly("org.projectlombok:lombok:+")
	annotationProcessor("org.projectlombok:lombok:+")
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

tasks.jar {
	archiveBaseName = "Transport-Simulation-Core-Build-Tools"
}

tasks.withType<AbstractArchiveTask> {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
	exclude("website/**")
}
