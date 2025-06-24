package org.mtr.core;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class WebserverSetup {

	private static final Logger LOGGER = LogManager.getLogger("WebserverSetup");

	public static void setup(File projectPath, String module, String namespace) {
		final Path websitePath = projectPath.toPath().resolve("buildSrc/src/main/resources/website/dist/website/browser");
		final StringBuilder stringBuilder = new StringBuilder(String.format("package org.mtr.%s%sgenerated;", namespace, namespace.isEmpty() ? "" : "."));
		stringBuilder.append("@javax.annotation.Nullable public final class WebserverResources{public static String get(String resource){switch(resource.startsWith(\"/\")?resource.substring(1):resource){");
		iterateFiles(websitePath, stringBuilder);
		stringBuilder.append("default:return null;}}}");
		write(projectPath.toPath().resolve(String.format("%ssrc/main/java/org/mtr/%s%sgenerated/WebserverResources.java", module, namespace, namespace.isEmpty() ? "" : "/")), stringBuilder.toString());

		write(projectPath.toPath().resolve("buildSrc/src/main/resources/website/.gitignore"), download("https://raw.githubusercontent.com/angular/angular/refs/heads/main/.gitignore"));
	}

	private static void iterateFiles(Path path, StringBuilder stringBuilder) {
		try (final Stream<Path> stream = Files.list(path)) {
			stream.forEach(innerPath -> {
				if (Files.isDirectory(innerPath)) {
					iterateFiles(innerPath, stringBuilder);
				} else {
					try {
						final String text = FileUtils.readFileToString(innerPath.toFile(), StandardCharsets.UTF_8);
						final List<String> splitText = new ArrayList<>();
						for (int i = 0; i < text.length(); i += 32768) {
							splitText.add(String.format("\"%s\"", text.substring(i, Math.min(text.length(), i + 32768)).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replaceAll("[\r\t]", "")));
						}
						stringBuilder.append(String.format("case \"%s\":return new StringBuilder(%s).toString();", innerPath.getFileName().toString(), String.join(").append(", splitText)));
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
			return IOUtils.toString(new URL(url), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("", e);
			return "";
		}
	}

	private static void write(Path path, String content) {
		try {
			Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}
}
