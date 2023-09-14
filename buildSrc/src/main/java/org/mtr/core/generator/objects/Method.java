package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

import javax.annotation.Nullable;
import java.util.stream.Collectors;

public class Method implements GeneratedObject {

	public final ObjectArraySet<String> annotations = new ObjectArraySet<>();
	public final ObjectArraySet<OtherModifier> otherModifiers = new ObjectArraySet<>();
	public final ObjectArrayList<Parameter> parameters = new ObjectArrayList<>();
	public final ObjectArrayList<String> content = new ObjectArrayList<>();
	private final VisibilityModifier visibilityModifier;
	private final String name;
	private final Type returnType;

	public Method(VisibilityModifier visibilityModifier, @Nullable Type returnType, String name) {
		this.visibilityModifier = visibilityModifier;
		this.name = name;
		this.returnType = returnType;
	}

	@Override
	public ObjectArrayList<String> generate() {
		final ObjectArrayList<String> result = new ObjectArrayList<>();
		annotations.forEach(annotation -> result.add(String.format("@%s", annotation)));
		final boolean isAbstract = otherModifiers.contains(OtherModifier.ABSTRACT);

		final StringBuilder stringBuilder = new StringBuilder(visibilityModifier.name).append(' ');
		otherModifiers.forEach(otherModifier -> stringBuilder.append(otherModifier.name).append(' '));
		stringBuilder.append(returnType == null ? "void" : returnType.name).append(' ');
		stringBuilder.append(name).append('(');
		stringBuilder.append(parameters.stream().map(parameter -> String.join(" ", parameter.generate())).collect(Collectors.joining(", "))).append(")");

		if (isAbstract) {
			stringBuilder.append(";");
			result.add(stringBuilder.toString());
		} else {
			stringBuilder.append(" {");
			result.add(stringBuilder.toString());
			content.forEach(line -> result.add(String.format("\t%s", line)));
			result.add("}");
		}

		return result;
	}
}
