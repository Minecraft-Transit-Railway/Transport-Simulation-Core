package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.stream.Collectors;

public class Constructor implements GeneratedObject {

	public final ObjectArrayList<Parameter> parameters = new ObjectArrayList<>();
	public final ObjectArrayList<Parameter> superParameters = new ObjectArrayList<>();
	public final ObjectArrayList<String> content = new ObjectArrayList<>();
	private final VisibilityModifier visibilityModifier;
	private final String name;

	public Constructor(VisibilityModifier visibilityModifier, String name) {
		this.visibilityModifier = visibilityModifier;
		this.name = name;
	}

	@Override
	public ObjectArrayList<String> generateJava() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		result.add(String.format("%s %s(%s) {", visibilityModifier.name, name, getAllParameters().stream().map(parameter -> String.join(" ", parameter.generateJava())).collect(Collectors.joining(", "))));

		if (!superParameters.isEmpty()) {
			result.add(String.format("\tsuper(%s);", Parameter.getParameterNames(superParameters)));
		}

		content.forEach(line -> result.add(String.format("\t%s", line)));
		result.add("}");
		return result;
	}

	@Override
	public ObjectArrayList<String> generateTypeScript() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		result.add(String.format("%s constructor(%s) {", visibilityModifier.name, getAllParameters().stream().map(parameter -> String.join(" ", parameter.generateTypeScript())).collect(Collectors.joining(", "))));

		if (!superParameters.isEmpty()) {
			result.add(String.format("\tsuper(%s);", Parameter.getParameterNames(superParameters)));
		}

		content.forEach(line -> result.add(String.format("\t%s", line)));
		result.add("}");
		return result;
	}

	private ObjectArrayList<Parameter> getAllParameters() {
		final ObjectArrayList<Parameter> allParameters = new ObjectArrayList<>();
		allParameters.addAll(parameters);
		allParameters.addAll(superParameters);
		return allParameters;
	}
}
