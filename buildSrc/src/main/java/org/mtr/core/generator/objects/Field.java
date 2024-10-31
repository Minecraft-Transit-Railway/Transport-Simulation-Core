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
	public ObjectArrayList<String> generateJava() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		final StringBuilder stringBuilder = new StringBuilder(visibilityModifier.name).append(' ');
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append(type.nameJava).append(' ').append(name).append(type.getInitializerJava(defaultValue, useDefaultInitializer));
		result.add(stringBuilder.toString());

		return result;
	}

	@Override
	public ObjectArrayList<String> generateTypeScript() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		final StringBuilder stringBuilder = new StringBuilder(visibilityModifier.name).append(' ');
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append(name).append(": ").append(type.nameTypeScript).append(type.getInitializerTypeScript(defaultValue, useDefaultInitializer));
		result.add(stringBuilder.toString());

		return result;
	}
}
