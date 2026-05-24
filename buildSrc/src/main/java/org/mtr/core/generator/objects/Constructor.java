package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.stream.Collectors;

/**
 * Code-model representation of a single constructor declaration.
 *
 * <p>After construction, callers populate {@link #parameters}, {@link #superParameters},
 * and {@link #content} before calling {@link #generateJava()} or
 * {@link #generateTypeScript()} to obtain the rendered source lines.</p>
 */
public class Constructor implements GeneratedObject {

	/**
	 * Parameters declared directly on this constructor (appear before {@link #superParameters}).
	 */
	public final ObjectArrayList<Parameter> parameters = new ObjectArrayList<>();
	/**
	 * Parameters that are forwarded verbatim to the {@code super(…)} call.
	 * These are appended after {@link #parameters} in the parameter list and
	 * used to build the {@code super(…)} statement inside the body.
	 */
	public final ObjectArrayList<Parameter> superParameters = new ObjectArrayList<>();
	/**
	 * Body statements, one per list entry, printed with a single leading tab.
	 */
	public final ObjectArrayList<String> content = new ObjectArrayList<>();
	private final VisibilityModifier visibilityModifier;
	private final String name;

	/**
	 * Creates a new constructor model node.
	 *
	 * @param visibilityModifier the access visibility of the constructor
	 * @param name               the simple class name (used as the constructor name)
	 */
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
