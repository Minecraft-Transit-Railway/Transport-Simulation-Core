package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.jspecify.annotations.Nullable;

/**
 * Code-model representation of a single field (member variable) declaration.
 *
 * <p>Callers may mutate {@link #otherModifiers} and {@link #annotations} after
 * construction.  Rendering is performed by {@link #generateJava()} and
 * {@link #generateTypeScript()}.</p>
 */
public class Field implements GeneratedObject {

	/**
	 * Extra non-visibility modifiers (e.g. {@code final}, {@code static}) applied to the field.
	 */
	public final ObjectArraySet<OtherModifier> otherModifiers = new ObjectArraySet<>();
	/**
	 * Annotations placed inline on the field declaration (without the leading {@code @}).
	 */
	public final ObjectArraySet<String> annotations = new ObjectArraySet<>();
	private final VisibilityModifier visibilityModifier;
	private final Type type;
	private final String name;
	@Nullable
	private final String defaultValue;
	private final boolean useDefaultInitializer;

	/**
	 * Creates a field whose initial value is determined by the type's default initialiser.
	 *
	 * @param visibilityModifier    the access visibility of the field
	 * @param type                  the declared type
	 * @param name                  the field name
	 * @param useDefaultInitializer {@code true} to emit the type's built-in default initialiser
	 *                              (e.g. {@code new ArrayList<>()}); {@code false} for no initialiser
	 */
	public Field(VisibilityModifier visibilityModifier, Type type, String name, boolean useDefaultInitializer) {
		this.visibilityModifier = visibilityModifier;
		this.type = type;
		this.name = name;
		defaultValue = null;
		this.useDefaultInitializer = useDefaultInitializer;
	}

	/**
	 * Creates a field with an explicit literal default value.
	 *
	 * @param visibilityModifier the access visibility of the field
	 * @param type               the declared type
	 * @param name               the field name
	 * @param defaultValue       the literal initialiser expression to emit (e.g. {@code "0"} or {@code "\"\""}),
	 *                           formatted via the type's formatter if one is defined
	 */
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
		annotations.forEach(annotation -> stringBuilder.append('@').append(annotation).append(' '));
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
