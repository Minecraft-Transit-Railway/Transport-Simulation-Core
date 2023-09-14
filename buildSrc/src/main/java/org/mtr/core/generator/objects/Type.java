package org.mtr.core.generator.objects;

import javax.annotation.Nullable;
import java.util.Objects;

public class Type {

	public final String name;
	public final boolean isArray;
	private final String initializer;
	private final String formatter;

	public static final Type BOOLEAN = createObject("boolean");
	public static final Type INTEGER = createObject("long");
	public static final Type NUMBER = createObject("double");
	public static final Type STRING = new Type("String", false, "", "\"%s\"");
	public static final Type BOOLEAN_ARRAY = createPrimitiveArray("it.unimi.dsi.fastutil.booleans.BooleanArrayList");
	public static final Type INTEGER_ARRAY = createPrimitiveArray("it.unimi.dsi.fastutil.longs.LongArrayList");
	public static final Type NUMBER_ARRAY = createPrimitiveArray("it.unimi.dsi.fastutil.doubles.DoubleArrayList");
	public static final Type STRING_ARRAY = createArray(STRING.name);

	private Type(String name, boolean isArray) {
		this(name, isArray, null, null);
	}

	private Type(String name, boolean isArray, @Nullable String initializer, @Nullable String formatter) {
		this.name = name;
		this.isArray = isArray;
		this.initializer = isArray ? String.format("new %s()", name) : initializer;
		this.formatter = formatter;
	}

	public String getInitializer(@Nullable String defaultValue, boolean useDefaultInitializer) {
		if (defaultValue == null) {
			return useDefaultInitializer && initializer != null ? formatInitializer(initializer) : "";
		} else {
			return formatInitializer(defaultValue);
		}
	}

	private String formatInitializer(String newInitializer) {
		return String.format(" = %s", formatter == null ? newInitializer : String.format(formatter, newInitializer));
	}

	public static Type createArray(String name) {
		return new Type(String.format("it.unimi.dsi.fastutil.objects.ObjectArrayList<%s>", name), true);
	}

	public static Type createObject(String name) {
		return new Type(name, false);
	}

	public static Type createEnum(String name) {
		return new Type(name, false, String.format("%s.values()[0]", name), null);
	}

	private static Type createPrimitiveArray(String name) {
		return new Type(name, true);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Type && Objects.equals(((Type) obj).name, name);
	}
}
