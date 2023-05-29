package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public class Class implements GeneratedObject {

	public final ObjectArrayList<String> imports = new ObjectArrayList<>();
	public final ObjectArraySet<OtherModifier> otherModifiers = new ObjectArraySet<>();
	public final ObjectArrayList<Field> fields = new ObjectArrayList<>();
	public final ObjectArrayList<Constructor> constructors = new ObjectArrayList<>();
	public final ObjectArrayList<Method> methods = new ObjectArrayList<>();
	public final ObjectArrayList<String> implementsClasses = new ObjectArrayList<>();
	private final String name;
	private final String extendsClass;
	private final String packageName;

	public Class(String name, String extendsClass, String packageName) {
		this.name = name;
		this.extendsClass = extendsClass;
		this.packageName = packageName;
	}

	@Override
	public ObjectArrayList<String> generate() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();

		result.add(String.format("package %s;", packageName));
		result.add("");

		imports.forEach(text -> result.add(String.format("import %s;", text)));
		result.add("");

		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("public ");
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append("class ").append(name);
		if (extendsClass != null) {
			stringBuilder.append(" extends ").append(extendsClass);
		}
		if (!implementsClasses.isEmpty()) {
			stringBuilder.append(" implements ").append(String.join(", ", implementsClasses));
		}
		stringBuilder.append(" {");
		result.add(stringBuilder.toString());

		appendWithTab(result, fields, true);
		appendWithTab(result, constructors, false);
		appendWithTab(result, methods, false);

		result.add("}");
		return result;
	}

	public Constructor createConstructor(VisibilityModifier visibilityModifier) {
		final Constructor constructor = new Constructor(visibilityModifier, name);
		constructors.add(constructor);
		return constructor;
	}

	private static <T extends GeneratedObject> void appendWithTab(ObjectArrayList<String> result, ObjectArrayList<T> generatedObjects, boolean addSemicolon) {
		generatedObjects.forEach(generatedObject -> {
			result.add("");
			generatedObject.generate().forEach(line -> result.add(String.format("\t%s%s", line, addSemicolon ? ";" : "")));
		});
	}
}
