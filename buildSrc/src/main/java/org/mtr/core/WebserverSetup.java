package org.mtr.core;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Gradle build-tool helper that embeds the compiled Angular web application into
 * a generated Java source file ({@code WebserverResources.java}).
 *
 * <p>The generated class contains a single {@code get(String resource)} method
 * that uses a {@code switch} statement to return the text content of each static
 * asset bundled from {@code website/dist/website/browser/}.  This makes the
 * front-end self-contained inside the JAR with no external file-system dependency
 * at runtime.</p>
 *
 * <p>In addition, the canonical Angular {@code .gitignore} is downloaded and
 * written to the {@code website/} directory so that generated files are
 * automatically excluded from version control.</p>
 */
public final class WebserverSetup {

	private static final Logger LOGGER = LogManager.getLogger("WebserverSetup");

	/**
	 * Performs the webserver resource embedding.
	 *
	 * <ol>
	 *   <li>Recursively reads every file under
	 *       {@code <projectPath>/website/dist/website/browser/}.</li>
	 *   <li>Encodes each file's content as a Java string literal (splitting at
	 *       32 768-character boundaries to avoid constant-pool limits).</li>
	 *   <li>Writes the resulting {@code WebserverResources.java} class to
	 *       {@code <module>src/main/java/org/mtr/<namespace>/generated/}.</li>
	 *   <li>Downloads the canonical Angular {@code .gitignore} into
	 *       {@code <projectPath>/website/.gitignore}.</li>
	 * </ol>
	 *
	 * @param projectPath the root directory of the Gradle project
	 * @param module      an optional sub-module path prefix (empty string for the root module)
	 * @param namespace   the Java package segment used in the generated class
	 *                    (e.g. {@code "core"} → package {@code org.mtr.core.generated})
	 */
	public static void setup(File projectPath, String module, String namespace) {
		final Path websitePath = projectPath.toPath().resolve("website/dist/website/browser");
		final StringBuilder stringBuilder = new StringBuilder(String.format("package org.mtr.%s%sgenerated;", namespace, namespace.isEmpty() ? "" : "."));
		stringBuilder.append("public final class WebserverResources{@org.jspecify.annotations.Nullable public static String get(String resource){switch(resource.startsWith(\"/\")?resource.substring(1):resource){");
		iterateFiles(websitePath, Path.of(""), stringBuilder);
		stringBuilder.append("default:return null;}}}");
		write(projectPath.toPath().resolve(String.format("%ssrc/main/java/org/mtr/%s%sgenerated/WebserverResources.java", module, namespace, namespace.isEmpty() ? "" : "/")), stringBuilder.toString());

		write(projectPath.toPath().resolve("website/.gitignore"), download("https://raw.githubusercontent.com/angular/angular/refs/heads/main/.gitignore"));
	}

	private static void iterateFiles(Path rootPath, Path innerPath, StringBuilder stringBuilder) {
		try (final Stream<Path> stream = Files.list(rootPath.resolve(innerPath))) {
			stream.forEach(filePath -> {
				final Path resolvedInnerPath = innerPath.resolve(filePath.getFileName());
				if (Files.isDirectory(filePath)) {
					iterateFiles(rootPath, resolvedInnerPath, stringBuilder);
				} else {
					try {
						final String text = FileUtils.readFileToString(filePath.toFile(), StandardCharsets.UTF_8);
						final List<String> splitText = new ArrayList<>();
						for (int i = 0; i < text.length(); i += 32768) {
							splitText.add(String.format("\"%s\"", text.substring(i, Math.min(text.length(), i + 32768)).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replaceAll("[\r\t]", "")));
						}
						stringBuilder.append(String.format("case \"%s\":return new StringBuilder(%s).toString();", resolvedInnerPath.toString().replace("\\", "/"), String.join(").append(", splitText)));
					} catch (Exception e) {
						LOGGER.error("", e);
					}
				}
			});
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}

	private static String download(String url) {
		try {
			return IOUtils.toString(URI.create(url).toURL(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("", e);
			return "";
		}
	}

	private static void write(Path path, String content) {
		try {
			Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}
}
