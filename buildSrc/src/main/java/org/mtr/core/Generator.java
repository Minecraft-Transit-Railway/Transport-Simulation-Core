package org.mtr.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.gradle.api.Project;
import org.mtr.core.generator.objects.Class;
import org.mtr.core.generator.objects.Method;
import org.mtr.core.generator.objects.OtherModifier;
import org.mtr.core.generator.objects.VisibilityModifier;
import org.mtr.core.generator.schema.SchemaParserJava;
import org.mtr.core.generator.schema.SchemaParserTypeScript;
import org.mtr.core.generator.schema.Utilities;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
public final class Generator {

	private static final Logger LOGGER = LogManager.getLogger("Generator");

	public static void generateJava(Project project, String inputPath, String outputPath, boolean writeTests, String... imports) {
		final Object2ObjectAVLTreeMap<String, SchemaParserJava> schemaParsers = new Object2ObjectAVLTreeMap<>();
		final String outputPackage = outputPath.replace("/", ".");
		final Class testClass = new Class("SchemaTests", null, "org.mtr." + outputPackage);
		setImports(testClass, imports);
		testClass.imports.add("org.junit.jupiter.api.*");
		testClass.implementsClasses.add("TestUtilities");
		testClass.otherModifiers.add(OtherModifier.FINAL);

		try (final Stream<Path> schemasStream = Files.list(project.getRootDir().toPath().resolve("buildSrc/src/main/resources").resolve(inputPath))) {
			schemasStream.forEach(path -> {
				try {
					final JsonObject jsonObject = JsonParser.parseString(FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8)).getAsJsonObject();
					final String className = Utilities.formatRefName(path.getFileName().toString());
					final String schemaClassName = formatClassName(className);
					final JsonObject extendsObject = jsonObject.getAsJsonObject("extends");
					final String extendsClassName = extendsObject == null ? null : formatClassName(Utilities.formatRefName(extendsObject.get("$ref").getAsString()));

					final Class schemaClass = new Class(schemaClassName, Utilities.getStringOrNull(jsonObject.get("javaExtends")), "org.mtr." + outputPackage);
					setImports(schemaClass, imports);
					schemaClass.otherModifiers.add(OtherModifier.ABSTRACT);

					final Method testMethod = new Method(VisibilityModifier.PUBLIC, null, String.format("test%s", schemaClassName));
					testMethod.annotations.add("RepeatedTest(10)");
					testMethod.content.add(String.format("final %1$s data = TestUtilities.random%1$s();", className));
					testMethod.content.add(String.format("TestUtilities.serializeAndDeserialize(data, TestUtilities::new%s);", className));

					schemaParsers.put(schemaClassName, new SchemaParserJava(schemaClass, extendsClassName, testMethod, jsonObject));
				} catch (Exception e) {
					LOGGER.error("", e);
				}
			});
		} catch (Exception e) {
			LOGGER.error("", e);
		}

		final Path projectPath = project.getProjectDir().toPath();

		schemaParsers.forEach((schemaClassName, schemaParserJava) -> {
			try {
				FileUtils.write(projectPath.resolve("src/main/java/org/mtr").resolve(outputPath).resolve(schemaClassName + ".java").toFile(), schemaParserJava.generateSchemaClass(schemaParsers, testClass), StandardCharsets.UTF_8);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		});

		if (writeTests) {
			try {
				FileUtils.write(projectPath.resolve("src/test/java/org/mtr").resolve(outputPath).resolve("SchemaTests.java").toFile(), String.join("\n", testClass.generateJava()), StandardCharsets.UTF_8);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		}

		try {
			FileUtils.write(
					projectPath.resolve("src/main/java/org/mtr").resolve(outputPath).resolve("package-info.java").toFile(),
					String.format("@ParametersAreNonnullByDefault\npackage org.mtr.%s;\n\nimport javax.annotation.ParametersAreNonnullByDefault;", outputPackage),
					StandardCharsets.UTF_8
			);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}

	public static void generateTypeScript(Project project, String inputPath, String outputPath) {
		final Object2ObjectAVLTreeMap<String, SchemaParserTypeScript> schemaParsers = new Object2ObjectAVLTreeMap<>();

		try (final Stream<Path> schemasStream = Files.list(project.getRootDir().toPath().resolve("buildSrc/src/main/resources").resolve(inputPath))) {
			schemasStream.forEach(path -> {
				try {
					final JsonObject jsonObject = JsonParser.parseString(FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8)).getAsJsonObject();
					final String fileName = path.getFileName().toString();
					final JsonObject extendsObject = jsonObject.getAsJsonObject("extends");
					final String extendsClassName = extendsObject == null ? null : formatClassName(Utilities.formatRefName(extendsObject.get("$ref").getAsString()));

					final Class schemaClass = new Class(Utilities.formatRefName(fileName), null, "");
					schemaClass.otherModifiers.add(OtherModifier.ABSTRACT);

					schemaParsers.put(Utilities.formatRefNameRaw(fileName), new SchemaParserTypeScript(schemaClass, extendsClassName, jsonObject));
					schemaClass.imports.remove(Utilities.formatRefNameRaw(fileName));
				} catch (Exception e) {
					LOGGER.error("", e);
				}
			});
		} catch (Exception e) {
			LOGGER.error("", e);
		}

		final Path generatedPath = project.getProjectDir().toPath().resolve(outputPath);

		schemaParsers.forEach((className, schemaParserTypeScript) -> {
			try {
				FileUtils.write(generatedPath.resolve(className + ".ts").toFile(), schemaParserTypeScript.generateSchemaClass(schemaParsers), StandardCharsets.UTF_8);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		});
	}

	private static String formatClassName(String text) {
		return String.format("%sSchema", text);
	}

	private static void setImports(Class newClass, String... imports) {
		newClass.imports.add("org.mtr.core.serializer.*");
		newClass.imports.add("org.mtr.core.tool.*");
		newClass.imports.add("javax.annotation.*");
		for (final String importPackage : imports) {
			newClass.imports.add(String.format("org.mtr.%s.*", importPackage));
		}
	}
}
