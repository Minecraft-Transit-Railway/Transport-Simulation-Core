package org.mtr.core;

/**
 * Build-time generated version constants for Transport Simulation Core.
 *
 * <p>The actual file under {@code src/main/java/org/mtr/core/Version.java} is regenerated on every
 * Gradle build from this template by the {@code copy} block in {@code build.gradle}, with the
 * {@code @version@} token replaced by the current Asia/Hong_Kong timestamp ({@code yyyyMMdd-HHmmss}).
 * Edit the template, not the generated file.</p>
 */
public interface Version {

	/**
	 * Human-readable build identifier of the form {@code "build-yyyyMMdd-HHmmss"}.
	 *
	 * <p>Used by the dashboard footer and the {@code /mtr/api/map} responses so operators can
	 * confirm which build is running. The value is regenerated on every Gradle build.</p>
	 */
	String VERSION = "build-@version@";
}
