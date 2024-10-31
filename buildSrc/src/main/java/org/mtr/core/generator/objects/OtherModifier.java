package org.mtr.core.generator.objects;

public enum OtherModifier {
	ABSTRACT("abstract"),
	STATIC("static"),
	FINAL("final"),
	READONLY("readonly");

	public final String name;

	OtherModifier(String name) {
		this.name = name;
	}
}
