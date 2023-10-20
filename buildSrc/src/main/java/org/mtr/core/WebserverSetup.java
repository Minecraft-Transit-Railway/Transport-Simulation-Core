package org.mtr.core;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class WebserverSetup {

	private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	public static void setup(File projectPath) {
		final Path websitePath = projectPath.toPath().resolve("buildSrc/src/main/resources/website");
		write(websitePath.resolve("three.module.min.js"), download("https://raw.githubusercontent.com/mrdoob/three.js/dev/build/three.module.min.js"));
		write(websitePath.resolve("BufferGeometryUtils.js"), download("https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/jsm/utils/BufferGeometryUtils.js").replace("from 'three'", "from './three.module.min.js'"));

		final StringBuilder stringBuilder = new StringBuilder("package org.mtr.core.generated;@javax.annotation.Nullable public final class WebserverResources{public static String get(String resource){switch(resource.startsWith(\"/\")?resource.substring(1):resource){");
		try (final Stream<Path> stream = Files.list(websitePath)) {
			stream.forEach(websiteFilePath -> {
				try {
					final String text = FileUtils.readFileToString(websiteFilePath.toFile(), StandardCharsets.UTF_8).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replaceAll("[\r\t]", "");
					final List<String> splitText = new ArrayList<>();
					for (int i = 0; i < text.length(); i += 32768) {
						splitText.add(String.format("\"%s\"", text.substring(i, Math.min(text.length(), i + 32768))));
					}
					stringBuilder.append(String.format("case \"%s\":return new StringBuilder(%s).toString();", websiteFilePath.getFileName().toString(), String.join(").append(", splitText)));
				} catch (Exception e) {
					logException(e);
				}
			});
		} catch (Exception e) {
			logException(e);
		}
		stringBuilder.append("default:return null;}}}");
		write(projectPath.toPath().resolve("src/main/java/org/mtr/core/generated/WebserverResources.java"), stringBuilder.toString());
	}

	private static String download(String url) {
		try {
			return IOUtils.toString(new URL(url), StandardCharsets.UTF_8);
		} catch (Exception e) {
			logException(e);
			return "";
		}
	}

	private static void write(Path path, String content) {
		try {
			Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (Exception e) {
			logException(e);
		}
	}

	private static void logException(Exception e) {
		LOGGER.log(Level.INFO, e.getMessage(), e);
	}
}