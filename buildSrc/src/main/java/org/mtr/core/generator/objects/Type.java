package org.mtr.core.generator.objects;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class Type {

	public final String nameJava;
	public final String nameTypeScript;
	public final boolean isArray;
	private final String initializerJava;
	private final String initializerTypeScript;
	private final String formatter;

	public static final Type BOOLEAN = createObject("boolean", "boolean");
	public static final Type INTEGER = createObject("long", "number");
	public static final Type NUMBER = createObject("double", "number");
	public static final Type STRING = new Type("String", "string", false, "", "", "\"%s\"");
	public static final Type BOOLEAN_ARRAY = createPrimitiveArray("org.mtr.libraries.it.unimi.dsi.fastutil.booleans.BooleanArrayList", "boolean[]");
	public static final Type INTEGER_ARRAY = createPrimitiveArray("org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList", "number[]");
	public static final Type NUMBER_ARRAY = createPrimitiveArray("org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleArrayList", "number[]");
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

	public String getInitializerJava(@Nullable String defaultValue, boolean useDefaultInitializer) {
		if (defaultValue == null) {
			return useDefaultInitializer && initializerJava != null ? formatInitializer(initializerJava) : "";
		} else {
			return formatInitializer(defaultValue);
		}
	}

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

	public static Type createArray(String nameJava, String nameTypeScript) {
		return new Type(String.format("org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList<%s>", nameJava), String.format("%s[]", nameTypeScript), true);
	}

	public static Type createObject(String nameJava, String nameTypeScript) {
		return new Type(nameJava, nameTypeScript, false);
	}

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
