package org.mtr.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.apache.commons.io.FileUtils;
import org.mtr.core.generator.objects.Class;
import org.mtr.core.generator.objects.Method;
import org.mtr.core.generator.objects.OtherModifier;
import org.mtr.core.generator.objects.VisibilityModifier;
import org.mtr.core.generator.schema.SchemaParser;
import org.mtr.core.generator.schema.Utilities;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
public class Generator {

	private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

	public static void generate(File projectPath) {
		final Object2ObjectAVLTreeMap<String, SchemaParser> schemaParsers = new Object2ObjectAVLTreeMap<>();
		final Class testClass = new Class("SchemaTests", null, "org.mtr.core.generated");
		setImports(testClass);
		testClass.imports.add("org.junit.jupiter.api.*");
		testClass.implementsClasses.add("TestUtilities");
		testClass.otherModifiers.add(OtherModifier.FINAL);

		try (final Stream<Path> schemasStream = Files.list(projectPath.toPath().resolve("buildSrc/src/main/resources/schemas"))) {
			schemasStream.forEach(path -> {
				try {
					final JsonObject jsonObject = JsonParser.parseString(FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8)).getAsJsonObject();
					final String className = Utilities.formatRefName(path.getFileName().toString());
					final String schemaClassName = formatClassName(className);
					final JsonObject extendsObject = jsonObject.getAsJsonObject("extends");
					final String extendsClassName = extendsObject == null ? null : formatClassName(Utilities.formatRefName(extendsObject.get("$ref").getAsString()));

					final Class schemaClass = new Class(schemaClassName, Utilities.getStringOrNull(jsonObject.get("javaExtends")), "org.mtr.core.generated");
					setImports(schemaClass);
					schemaClass.otherModifiers.add(OtherModifier.ABSTRACT);

					final Method testMethod = new Method(VisibilityModifier.PUBLIC, null, String.format("test%s", schemaClassName));
					testMethod.annotations.add("RepeatedTest(10)");
					testMethod.content.add(String.format("final %1$s data = TestUtilities.random%1$s();", className));
					testMethod.content.add(String.format("TestUtilities.serializeAndDeserialize(data, TestUtilities::new%s);", className));

					schemaParsers.put(schemaClassName, new SchemaParser(schemaClass, extendsClassName, testMethod, jsonObject));
				} catch (Exception e) {
					logException(e);
				}
			});
		} catch (Exception e) {
			logException(e);
		}

		try {
			FileUtils.deleteDirectory(new File(String.format("%s/src/main/java/org/mtr/core/generated", projectPath)));
			FileUtils.deleteDirectory(new File(String.format("%s/src/test/java/org/mtr/core/generated", projectPath)));
		} catch (Exception e) {
			logException(e);
		}

		schemaParsers.forEach((schemaClassName, schemaParser) -> {
			try {
				FileUtils.write(new File(String.format("%s/src/main/java/org/mtr/core/generated/%s.java", projectPath, schemaClassName)), schemaParser.generateSchemaClass(schemaParsers, testClass), StandardCharsets.UTF_8);
			} catch (Exception e) {
				logException(e);
			}
		});

		try {
			FileUtils.write(new File(String.format("%s/src/test/java/org/mtr/core/generated/SchemaTests.java", projectPath)), String.join("\n", testClass.generate()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			logException(e);
		}

		try {
			FileUtils.write(
					new File(String.format("%s/src/main/java/org/mtr/core/generated/package-info.java", projectPath)),
					"@ParametersAreNonnullByDefault\npackage org.mtr.core.generated;\n\nimport javax.annotation.ParametersAreNonnullByDefault;",
					StandardCharsets.UTF_8
			);
		} catch (Exception e) {
			logException(e);
		}
	}

	private static String formatClassName(String text) {
		return String.format("%sSchema", text);
	}

	private static void setImports(Class newClass) {
		newClass.imports.add("org.mtr.core.client.*");
		newClass.imports.add("org.mtr.core.data.*");
		newClass.imports.add("org.mtr.core.serializers.*");
		newClass.imports.add("org.mtr.core.simulation.*");
		newClass.imports.add("org.mtr.core.tools.*");
		newClass.imports.add("javax.annotation.*");
	}

	private static void logException(Exception e) {
		LOGGER.log(Level.INFO, e.getMessage(), e);
	}
}
