package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.generator.schema.Utilities;

import javax.annotation.Nullable;
import java.util.function.Function;

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

		imports.forEach(text -> result.add(String.format("import {%s} from \"./%s\";", Utilities.capitalizeFirstLetter(text), text)));
		result.add("");

		getClassBody(result, new StringBuilder("export class ").append(name));

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
