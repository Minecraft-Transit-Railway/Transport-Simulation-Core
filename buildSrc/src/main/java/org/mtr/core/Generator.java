package org.mtr.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.apache.commons.io.FileUtils;
import org.mtr.core.generator.schema.SchemaParser;
import org.mtr.core.generator.schema.Utilities;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Generator {

	public static void generate(File projectPath) {
		final Object2ObjectAVLTreeMap<String, SchemaParser> schemaParsers = new Object2ObjectAVLTreeMap<>();

		try (final Stream<Path> schemasStream = Files.list(projectPath.toPath().resolve("buildSrc/src/main/resources/schemas"))) {
			schemasStream.forEach(path -> {
				try {
					final JsonObject jsonObject = JsonParser.parseString(FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8)).getAsJsonObject();
					final String className = Utilities.formatClassName(path.getFileName().toString());
					schemaParsers.put(className, new SchemaParser(className, "org.mtr.core.generated", jsonObject));
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}

		schemaParsers.forEach((className, schemaParser) -> {
			try {
				FileUtils.write(new File(String.format("%s/src/main/java/org/mtr/core/generated/%s.java", projectPath, className)), schemaParser.generate(schemaParsers), StandardCharsets.UTF_8);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
}
