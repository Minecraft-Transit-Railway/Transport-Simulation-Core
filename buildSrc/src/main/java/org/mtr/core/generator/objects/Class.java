package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.jspecify.annotations.Nullable;
import org.mtr.core.generator.schema.Utilities;

import java.util.function.Function;

/**
 * Code-model representation of a Java or TypeScript class declaration.
 *
 * <p>Callers build up the class by mutating the public collections ({@link #imports},
 * {@link #otherModifiers}, {@link #fields}, {@link #constructors}, {@link #methods},
 * {@link #implementsClasses}) and then call {@link #generateJava()} or
 * {@link #generateTypeScript()} to obtain the full rendered source.</p>
 */
public class Class implements GeneratedObject {

	/**
	 * Fully-qualified or wildcard import statements added to the generated file (Java only).
	 */
	public final ObjectArrayList<String> imports = new ObjectArrayList<>();
	/**
	 * Extra non-visibility modifiers applied to the class (e.g. {@link OtherModifier#ABSTRACT}, {@link OtherModifier#FINAL}).
	 */
	public final ObjectArraySet<OtherModifier> otherModifiers = new ObjectArraySet<>();
	/**
	 * Field declarations belonging to this class, in insertion order.
	 */
	public final ObjectArrayList<Field> fields = new ObjectArrayList<>();
	/**
	 * Constructor declarations belonging to this class, in insertion order.
	 */
	public final ObjectArrayList<Constructor> constructors = new ObjectArrayList<>();
	/**
	 * Method declarations belonging to this class, in insertion order.
	 */
	public final ObjectArrayList<Method> methods = new ObjectArrayList<>();
	/**
	 * Simple names of interfaces this class implements (Java) or extends (TypeScript).
	 */
	public final ObjectArrayList<String> implementsClasses = new ObjectArrayList<>();
	private final String name;
	@Nullable
	private final String extendsClass;
	private final String packageName;

	/**
	 * Creates a new class model node.
	 *
	 * @param name         the simple class name
	 * @param extendsClass the simple name of the superclass, or {@code null} if none
	 * @param packageName  the fully-qualified package name used in the {@code package} declaration
	 */
	public Class(String name, @Nullable String extendsClass, String packageName) {
		this.name = name;
		this.extendsClass = extendsClass;
		this.packageName = packageName;
	}

	@Override
	public ObjectArrayList<String> generateJava() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		result.add(String.format("package %s;", packageName));
		result.add("");

		imports.forEach(text -> result.add(String.format("import %s;", text)));
		result.add("");

		final StringBuilder stringBuilder = new StringBuilder("public ");
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append("class ").append(name);

		getClassBody(result, stringBuilder);

		appendWithTab(result, fields, GeneratedObject::generateJava, true);
		appendWithTab(result, constructors, GeneratedObject::generateJava, false);
		appendWithTab(result, methods, GeneratedObject::generateJava, false);

		result.add("}");
		return result;
	}

	@Override
	public ObjectArrayList<String> generateTypeScript() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		imports.forEach(text -> result.add(String.format("import {%sDTO} from \"./%s\";", Utilities.capitalizeFirstLetter(text), text)));
		result.add("");

		getClassBody(result, new StringBuilder("export class ").append(name).append("DTO"));

		appendWithTab(result, fields, GeneratedObject::generateTypeScript, true);
		appendWithTab(result, constructors, GeneratedObject::generateTypeScript, false);
		appendWithTab(result, methods, GeneratedObject::generateTypeScript, false);

		result.add("}");
		return result;
	}

	private void getClassBody(ObjectArrayList<String> result, StringBuilder stringBuilder) {
		if (extendsClass != null) {
			stringBuilder.append(" extends ").append(extendsClass);
		}
		if (!implementsClasses.isEmpty()) {
			stringBuilder.append(" implements ").append(String.join(", ", implementsClasses));
		}
		stringBuilder.append(" {");
		result.add(stringBuilder.toString());
	}

	/**
	 * Creates and registers a new {@link Constructor} for this class with the given visibility.
	 *
	 * @param visibilityModifier the access visibility of the constructor
	 * @return the newly created constructor, already added to {@link #constructors}
	 */
	public Constructor createConstructor(VisibilityModifier visibilityModifier) {
		final Constructor constructor = new Constructor(visibilityModifier, name);
		constructors.add(constructor);
		return constructor;
	}

	private static <T extends GeneratedObject> void appendWithTab(ObjectArrayList<String> result, ObjectArrayList<T> generatedObjects, Function<T, ObjectArrayList<String>> getGenerated, boolean addSemicolon) {
		generatedObjects.forEach(generatedObject -> {
			result.add("");
			getGenerated.apply(generatedObject).forEach(line -> result.add(String.format("\t%s%s", line, addSemicolon ? ";" : "")));
		});
	}
}
