package org.mtr.core.generator.objects;

public enum VisibilityModifier {
	PUBLIC("public"),
	PROTECTED("protected"),
	PRIVATE("private");

	public final String name;

	VisibilityModifier(String name) {
		this.name = name;
	}
}
