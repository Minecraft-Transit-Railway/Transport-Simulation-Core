package org.mtr.core.generator.objects;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a data type in the code model, storing both its Java and TypeScript name
 * along with the initialiser and formatting rules needed to render field declarations.
 *
 * <p>Instances are either pre-built constants ({@link #BOOLEAN}, {@link #INTEGER},
 * {@link #NUMBER}, {@link #STRING}, and array variants) or created via the static
 * factory methods ({@link #createObject}, {@link #createArray}, {@link #createEnum},
 * etc.).</p>
 */
public class Type {

	/**
	 * The Java type name as it appears in source (e.g. {@code "long"}, {@code "String"}).
	 */
	public final String nameJava;
	/**
	 * The TypeScript type name as it appears in source (e.g. {@code "number"}, {@code "string"}).
	 */
	public final String nameTypeScript;
	/**
	 * {@code true} if this type represents a collection / array type.
	 */
	public final boolean isArray;
	@Nullable
	private final String initializerJava;
	@Nullable
	private final String initializerTypeScript;
	@Nullable
	private final String formatter;

	/**
	 * Scalar boolean: Java {@code boolean} / TypeScript {@code boolean}.
	 */
	public static final Type BOOLEAN = createObject("boolean", "boolean");
	/**
	 * Scalar 64-bit integer: Java {@code long} / TypeScript {@code number}.
	 */
	public static final Type INTEGER = createObject("long", "number");
	/**
	 * Scalar floating-point: Java {@code double} / TypeScript {@code number}.
	 */
	public static final Type NUMBER = createObject("double", "number");
	/**
	 * Scalar string: Java {@code String} / TypeScript {@code string}.
	 */
	public static final Type STRING = new Type("String", "string", false, "", "", "\"%s\"");
	/**
	 * Array of booleans: Java {@code BooleanArrayList} / TypeScript {@code boolean[]}.
	 */
	public static final Type BOOLEAN_ARRAY = createPrimitiveArray("it.unimi.dsi.fastutil.booleans.BooleanArrayList", "boolean[]");
	/**
	 * Array of 64-bit integers: Java {@code LongArrayList} / TypeScript {@code number[]}.
	 */
	public static final Type INTEGER_ARRAY = createPrimitiveArray("it.unimi.dsi.fastutil.longs.LongArrayList", "number[]");
	/**
	 * Array of floating-point numbers: Java {@code DoubleArrayList} / TypeScript {@code number[]}.
	 */
	public static final Type NUMBER_ARRAY = createPrimitiveArray("it.unimi.dsi.fastutil.doubles.DoubleArrayList", "number[]");
	/**
	 * Array of strings: Java {@code ObjectArrayList<String>} / TypeScript {@code string[]}.
	 */
	public static final Type STRING_ARRAY = createArray(STRING.nameJava, STRING.nameTypeScript);

	private Type(String nameJava, String nameTypeScript, boolean isArray) {
		this(nameJava, nameTypeScript, isArray, null, null, null);
	}

	private Type(String nameJava, String nameTypeScript, boolean isArray, @Nullable String initializerJava, @Nullable String initializerTypeScript, @Nullable String formatter) {
		this.nameJava = nameJava;
		this.nameTypeScript = nameTypeScript;
		this.isArray = isArray;
		this.initializerJava = isArray ? String.format("new %s()", nameJava) : initializerJava;
		this.initializerTypeScript = isArray ? "[]" : initializerTypeScript;
		this.formatter = formatter;
	}

	/**
	 * Returns the Java initialiser fragment for a field using this type.
	 *
	 * @param defaultValue          an explicit default value expression, or {@code null} to use the built-in one
	 * @param useDefaultInitializer {@code true} to emit the built-in initialiser when {@code defaultValue} is {@code null}
	 * @return the initialiser string including the leading {@code " = "}, or an empty string if none applies
	 */
	public String getInitializerJava(@Nullable String defaultValue, boolean useDefaultInitializer) {
		if (defaultValue == null) {
			return useDefaultInitializer && initializerJava != null ? formatInitializer(initializerJava) : "";
		} else {
			return formatInitializer(defaultValue);
		}
	}

	/**
	 * Returns the TypeScript initialiser fragment for a field using this type.
	 *
	 * @param defaultValue          an explicit default value expression, or {@code null} to use the built-in one
	 * @param useDefaultInitializer {@code true} to emit the built-in initialiser when {@code defaultValue} is {@code null}
	 * @return the initialiser string including the leading {@code " = "}, or an empty string if none applies
	 */
	public String getInitializerTypeScript(@Nullable String defaultValue, boolean useDefaultInitializer) {
		if (defaultValue == null) {
			return useDefaultInitializer && initializerTypeScript != null ? formatInitializer(initializerTypeScript) : "";
		} else {
			return formatInitializer(defaultValue);
		}
	}

	private String formatInitializer(String newInitializer) {
		return String.format(" = %s", formatter == null ? newInitializer : String.format(formatter, newInitializer));
	}

	/**
	 * Creates an {@code ObjectArrayList}-backed array type for object elements.
	 *
	 * @param nameJava       the Java element type name
	 * @param nameTypeScript the TypeScript element type name
	 * @return a new array {@code Type}
	 */
	public static Type createArray(String nameJava, String nameTypeScript) {
		return new Type(String.format("it.unimi.dsi.fastutil.objects.ObjectArrayList<%s>", nameJava), String.format("%s[]", nameTypeScript), true);
	}

	/**
	 * Creates a simple scalar object type with no initialiser.
	 *
	 * @param nameJava       the Java type name
	 * @param nameTypeScript the TypeScript type name
	 * @return a new scalar {@code Type}
	 */
	public static Type createObject(String nameJava, String nameTypeScript) {
		return new Type(nameJava, nameTypeScript, false);
	}

	/**
	 * Creates a type representing a Java enum / TypeScript union-of-string-literals.
	 *
	 * <p>The Java default value is {@code EnumName.values()[0]} and the TypeScript default
	 * is the first pipe-separated alternative from {@code typeScriptEnum}.</p>
	 *
	 * @param nameJava       the Java enum class name
	 * @param typeScriptEnum a pipe-separated list of TypeScript literal values (e.g. {@code "FOO|BAR|BAZ"})
	 * @return a new enum {@code Type}
	 */
	public static Type createEnum(String nameJava, String typeScriptEnum) {
		return new Type(nameJava, Arrays.stream(typeScriptEnum.split("\\|")).map(typeScriptEnumPart -> String.format("\"%s\"", typeScriptEnumPart)).collect(Collectors.joining(" | ")), false, String.format("%s.values()[0]", nameJava), String.format("\"%s\"", typeScriptEnum.split("\\|")[0]), null);
	}

	private static Type createPrimitiveArray(String nameJava, String nameTypeScript) {
		return new Type(nameJava, nameTypeScript, true);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Type && Objects.equals(((Type) obj).nameJava, nameJava);
	}
}
