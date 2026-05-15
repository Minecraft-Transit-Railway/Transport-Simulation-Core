package org.mtr.core.generator.objects;

/**
 * Access-visibility modifiers used in the code model.  Each constant stores
 * the exact keyword string emitted when rendering to source code.
 */
public enum VisibilityModifier {
	/**
	 * {@code public} – accessible from any class.
	 */
	PUBLIC("public"),
	/**
	 * {@code protected} – accessible within the same package and subclasses.
	 */
	PROTECTED("protected"),
	/**
	 * {@code private} – accessible only within the declaring class.
	 */
	PRIVATE("private");

	/**
	 * The source-level keyword for this visibility modifier.
	 */
	public final String name;

	VisibilityModifier(String name) {
		this.name = name;
	}
}
