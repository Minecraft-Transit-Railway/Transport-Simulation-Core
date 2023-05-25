package org.mtr.core.generator.objects;

public enum Modifier {
	PUBLIC("public"),
	PROTECTED("protected"),
	PRIVATE("private"),
	ABSTRACT("abstract"),
	FINAL("final");

	public final String name;

	Modifier(String name) {
		this.name = name;
	}
}
