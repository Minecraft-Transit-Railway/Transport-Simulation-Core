package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.stream.Collectors;

/**
 * Code-model representation of a single method or constructor parameter.
 *
 * <p>When rendered to Java, parameters include the {@code final} modifier and
 * the Java type name.  When rendered to TypeScript, they use the TypeScript
 * name-colon-type convention.</p>
 */
public class Parameter implements GeneratedObject {

	private final Type type;
	private final String name;

	/**
	 * Creates a new parameter.
	 *
	 * @param type the declared type of the parameter
	 * @param name the parameter name
	 */
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

	/**
	 * Returns a comma-separated string of the parameter names from the given list,
	 * suitable for use as arguments in a {@code super(…)} call.
	 *
	 * @param superParameters the parameters whose names should be joined
	 * @return a comma-separated name list, e.g. {@code "foo, bar, baz"}
	 */
	protected static String getParameterNames(ObjectArrayList<Parameter> superParameters) {
		return superParameters.stream().map(parameter -> parameter.name).collect(Collectors.joining(", "));
	}
}
