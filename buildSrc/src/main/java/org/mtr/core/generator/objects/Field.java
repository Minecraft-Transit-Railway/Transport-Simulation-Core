package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public class Field implements GeneratedObject {

	public final ObjectArraySet<OtherModifier> otherModifiers = new ObjectArraySet<>();
	private final VisibilityModifier visibilityModifier;
	private final Type type;
	private final String name;
	private final String defaultValue;
	private final boolean useDefaultInitializer;

	public Field(VisibilityModifier visibilityModifier, Type type, String name, boolean useDefaultInitializer) {
		this.visibilityModifier = visibilityModifier;
		this.type = type;
		this.name = name;
		defaultValue = null;
		this.useDefaultInitializer = useDefaultInitializer;
	}

	public Field(VisibilityModifier visibilityModifier, Type type, String name, String defaultValue) {
		this.visibilityModifier = visibilityModifier;
		this.type = type;
		this.name = name;
		this.defaultValue = defaultValue;
		useDefaultInitializer = false;
	}

	@Override
	public ObjectArrayList<String> generate() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		final StringBuilder stringBuilder = new StringBuilder(visibilityModifier.name).append(' ');
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append(type.name).append(' ').append(name).append(type.getInitializer(defaultValue, useDefaultInitializer));
		result.add(stringBuilder.toString());

		return result;
	}
}
