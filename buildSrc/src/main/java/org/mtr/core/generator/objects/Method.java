package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.util.stream.Collectors;

public class Method implements GeneratedObject {

	public final ObjectArraySet<Modifier> modifiers = new ObjectArraySet<>();
	public final ObjectArrayList<Parameter> parameters = new ObjectArrayList<>();
	public final ObjectArrayList<String> content = new ObjectArrayList<>();
	private final String name;
	private final Type returnType;
	private final boolean isNotConstructor;

	private Method(String name, Type returnType, boolean isNotConstructor) {
		this.name = name;
		this.returnType = returnType;
		this.isNotConstructor = isNotConstructor;
	}

	@Override
	public ObjectArrayList<String> generate() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		final boolean isAbstract = modifiers.contains(Modifier.ABSTRACT);

		final StringBuilder stringBuilder = new StringBuilder();
		modifiers.forEach(modifier -> stringBuilder.append(modifier.name).append(' '));
		if (isNotConstructor) {
			stringBuilder.append(returnType == null ? "void" : returnType.name).append(' ');
		}
		stringBuilder.append(name).append('(');
		stringBuilder.append(parameters.stream().map(parameter -> String.join(" ", parameter.generate())).collect(Collectors.joining(", "))).append(")");
		if (isAbstract) {
			stringBuilder.append(";");
		} else {
			stringBuilder.append(" {");
		}
		result.add(stringBuilder.toString());

		if (!isAbstract) {
			content.forEach(line -> result.add(String.format("\t%s", line)));
			result.add("}");
		}

		return result;
	}

	public static Method createMethod(String name, Type returnType) {
		return new Method(name, returnType, true);
	}

	public static Method createConstructor(String name) {
		return new Method(name, null, false);
	}
}
