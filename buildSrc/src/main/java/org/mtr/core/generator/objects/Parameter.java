package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.stream.Collectors;

public class Parameter implements GeneratedObject {

	private final Type type;
	private final String name;

	public Parameter(Type type, String name) {
		this.type = type;
		this.name = name;
	}

	@Override
	public ObjectArrayList<String> generateJava() {
		return ObjectArrayList.of(String.format("%s %s %s", OtherModifier.FINAL.name, type.nameJava, name));
	}

	@Override
	public ObjectArrayList<String> generateTypeScript() {
		return ObjectArrayList.of(String.format("%s: %s", name, type.nameTypeScript));
	}

	protected static String getParameterNames(ObjectArrayList<Parameter> superParameters) {
		return superParameters.stream().map(parameter -> parameter.name).collect(Collectors.joining(", "));
	}
}
