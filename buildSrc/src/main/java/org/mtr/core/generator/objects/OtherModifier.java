package org.mtr.core.generator.objects;

/**
 * Non-visibility modifiers that can be applied to a class, field, or method in
 * the code model.  Each constant stores the exact keyword string used when
 * rendering to source code.
 */
public enum OtherModifier {
	/**
	 * {@code abstract} – the member has no concrete implementation.
	 */
	ABSTRACT("abstract"),
	/**
	 * {@code static} – the member belongs to the type rather than an instance.
	 */
	STATIC("static"),
	/**
	 * {@code final} – the member cannot be overridden or reassigned (Java).
	 */
	FINAL("final"),
	/**
	 * {@code readonly} – the member cannot be reassigned after initialisation (TypeScript).
	 */
	READONLY("readonly");

	/**
	 * The source-level keyword for this modifier.
	 */
	public final String name;

	OtherModifier(String name) {
		this.name = name;
	}
}
