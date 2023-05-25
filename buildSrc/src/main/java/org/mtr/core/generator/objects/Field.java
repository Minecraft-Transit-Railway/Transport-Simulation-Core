package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public class Field implements GeneratedObject {

	public final ObjectArraySet<Modifier> modifiers = new ObjectArraySet<>();
	private final Type type;
	public final String name;
	private final String defaultValue;
	private final boolean useDefaultInitializer;

	public Field(Type type, String name, boolean useDefaultInitializer) {
		this.type = type;
		this.name = name;
		defaultValue = null;
		this.useDefaultInitializer = useDefaultInitializer;
	}

	public Field(Type type, String name, String defaultValue) {
		this.type = type;
		this.name = name;
		this.defaultValue = defaultValue;
		useDefaultInitializer = false;
	}

	@Override
	public ObjectArrayList<String> generate() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		final StringBuilder stringBuilder = new StringBuilder();
		modifiers.forEach(modifier -> stringBuilder.append(modifier.name).append(' '));
		stringBuilder.append(type.name).append(' ').append(name).append(type.getInitializer(defaultValue, useDefaultInitializer));
		result.add(stringBuilder.toString());

		return result;
	}
}
