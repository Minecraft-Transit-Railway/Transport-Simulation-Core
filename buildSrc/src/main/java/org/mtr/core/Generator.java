package org.mtr.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.gradle.api.Project;
import org.mtr.core.generator.objects.Class;
import org.mtr.core.generator.objects.OtherModifier;
import org.mtr.core.generator.schema.SchemaParserJava;
import org.mtr.core.generator.schema.SchemaParserTypeScript;
import org.mtr.core.generator.schema.Utilities;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Entry point for the Gradle code-generation tasks.
 *
 * <p>This class exposes two static methods that are called from
 * {@code build.gradle.kts} via custom Gradle tasks:</p>
 * <ul>
 *   <li>{@link #generateJava} – reads JSON schemas and writes abstract Java
 *       schema classes.</li>
 *   <li>{@link #generateTypeScript} – reads JSON schemas and writes TypeScript
 *       DTO classes for the Angular front-end.</li>
 * </ul>
 *
 * <p>Schema files are looked up under
 * {@code buildSrc/src/main/resources/schema/}; see {@code docs/SCHEMA.md} for
 * the authoring workflow.</p>
 */
public final class Generator {

	private static final Logger LOGGER = LogManager.getLogger("Generator");

	/**
	 * Generates abstract Java schema classes from the JSON schema files found
	 * under {@code inputPath}.
	 *
	 * <p>For every {@code *.json} file in the schema directory an abstract class is
	 * written to {@code src/main/java/org/mtr/<outputPath>/<ClassName>Schema.java}.
	 * A {@code package-info.java} annotated with {@code @NullMarked} is always
	 * emitted.</p>
	 *
	 * @param project    the Gradle project, used to resolve the root and project directories
	 * @param inputPath  path relative to {@code buildSrc/src/main/resources} containing the
	 *                   source JSON schema files (e.g. {@code "schema/data"})
	 * @param outputPath path relative to {@code src/main/java/org/mtr} where the generated
	 *                   Java files will be written (e.g. {@code "core/generated/data"})
	 * @param imports    additional package suffixes (relative to {@code org.mtr}) whose
	 *                   wildcard imports are added to every generated class
	 */
	public static void generateJava(Project project, String inputPath, String outputPath, String... imports) {
		final String outputPackage = outputPath.replace("/", ".");
		final Object2ObjectAVLTreeMap<String, SchemaParserJava> schemaParsers = new Object2ObjectAVLTreeMap<>();

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

					schemaParsers.put(schemaClassName, new SchemaParserJava(schemaClass, extendsClassName, jsonObject));
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
				FileUtils.write(projectPath.resolve("src/main/java/org/mtr").resolve(outputPath).resolve(schemaClassName + ".java").toFile(), schemaParserJava.generateSchemaClass(schemaParsers), StandardCharsets.UTF_8);
			} catch (Exception e) {
				LOGGER.error("", e);
			}
		});

		try {
			FileUtils.write(
				projectPath.resolve("src/main/java/org/mtr").resolve(outputPath).resolve("package-info.java").toFile(),
				String.format(
					"/**%n"
						+ " * Generated schema classes for {@code org.mtr.%1$s}.%n"
						+ " *%n"
						+ " * <p>Do not edit by hand &mdash; these files are produced by the {@code Generator} task in%n"
						+ " * {@code buildSrc/} from the JSON schemas under%n"
						+ " * {@code buildSrc/src/main/resources/schema/}. See {@code docs/SCHEMA.md} for the%n"
						+ " * authoring workflow.</p>%n"
						+ " */%n"
						+ "@NullMarked%n"
						+ "package org.mtr.%1$s;%n"
						+ "%n"
						+ "import org.jspecify.annotations.NullMarked;",
					outputPackage
				),
				StandardCharsets.UTF_8
			);
		} catch (Exception e) {
			LOGGER.error("", e);
		}
	}

	/**
	 * Generates TypeScript DTO classes from the JSON schema files found under
	 * {@code inputPath} and writes them to {@code outputPath}.
	 *
	 * <p>For every {@code *.json} file in the schema directory a TypeScript file is
	 * written to {@code <outputPath>/<className>.ts}.</p>
	 *
	 * @param project    the Gradle project, used to resolve the project directory
	 * @param inputPath  path relative to {@code buildSrc/src/main/resources} containing the
	 *                   source JSON schema files (e.g. {@code "schema/map"})
	 * @param outputPath path relative to the project root where the TypeScript files are
	 *                   written (e.g. {@code "website/src/app/entity/generated"})
	 */
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
		newClass.imports.add("org.jspecify.annotations.*");
		for (final String importPackage : imports) {
			newClass.imports.add(String.format("org.mtr.%s.*", importPackage));
		}
	}
}
