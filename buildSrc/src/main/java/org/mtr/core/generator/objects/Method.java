package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.jspecify.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * Code-model representation of a single method declaration.
 *
 * <p>After construction, callers populate {@link #annotations}, {@link #otherModifiers},
 * {@link #parameters}, and {@link #content} before calling {@link #generateJava()} or
 * {@link #generateTypeScript()} to obtain the rendered source lines.</p>
 */
public class Method implements GeneratedObject {

	/**
	 * Annotation names placed above the method signature (without the leading {@code @}).
	 */
	public final ObjectArraySet<String> annotations = new ObjectArraySet<>();
	/**
	 * Extra non-visibility modifiers applied to the method (e.g. {@link OtherModifier#ABSTRACT}, {@link OtherModifier#STATIC}).
	 */
	public final ObjectArraySet<OtherModifier> otherModifiers = new ObjectArraySet<>();
	/**
	 * Ordered list of parameters accepted by the method.
	 */
	public final ObjectArrayList<Parameter> parameters = new ObjectArrayList<>();
	/**
	 * Body statements, one per list entry, printed with a single leading tab. Unused when the method is abstract.
	 */
	public final ObjectArrayList<String> content = new ObjectArrayList<>();
	private final VisibilityModifier visibilityModifier;
	private final String name;
	private final Type returnType;

	/**
	 * Creates a new method model node.
	 *
	 * @param visibilityModifier the access visibility of the method
	 * @param returnType         the return type, or {@code null} to render {@code void}
	 * @param name               the method name
	 */
	public Method(VisibilityModifier visibilityModifier, @Nullable Type returnType, String name) {
		this.visibilityModifier = visibilityModifier;
		this.name = name;
		this.returnType = returnType;
	}

	@Override
	public ObjectArrayList<String> generateJava() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		annotations.forEach(annotation -> result.add(String.format("@%s", annotation)));
		final boolean isAbstract = otherModifiers.contains(OtherModifier.ABSTRACT);

		final StringBuilder stringBuilder = new StringBuilder(visibilityModifier.name).append(' ');
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append(returnType == null ? "void" : returnType.nameJava).append(' ');
		stringBuilder.append(name).append('(');
		stringBuilder.append(parameters.stream().map(parameter -> String.join(" ", parameter.generateJava())).collect(Collectors.joining(", "))).append(")");

		getMethodBody(result, isAbstract, stringBuilder);
		return result;
	}

	@Override
	public ObjectArrayList<String> generateTypeScript() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		final boolean isAbstract = otherModifiers.contains(OtherModifier.ABSTRACT);

		final StringBuilder stringBuilder = new StringBuilder(visibilityModifier.name).append(' ');
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append(name).append("(");
		stringBuilder.append(parameters.stream().map(parameter -> String.join(" ", parameter.generateTypeScript())).collect(Collectors.joining(", "))).append("): ");
		stringBuilder.append(returnType == null ? "void" : returnType.nameTypeScript);

		getMethodBody(result, isAbstract, stringBuilder);
		return result;
	}

	private void getMethodBody(ObjectArrayList<String> result, boolean isAbstract, StringBuilder stringBuilder) {
		if (isAbstract) {
			stringBuilder.append(";");
			result.add(stringBuilder.toString());
		} else {
			stringBuilder.append(" {");
			result.add(stringBuilder.toString());
			content.forEach(line -> result.add(String.format("\t%s", line)));
			result.add("}");
		}
	}
}
