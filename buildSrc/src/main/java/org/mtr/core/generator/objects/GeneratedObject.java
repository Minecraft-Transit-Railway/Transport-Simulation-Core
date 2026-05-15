package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Implemented by every code-model object (class, field, method, etc.) that can
 * be rendered to source code.  Two target languages are supported: Java and
 * TypeScript.
 */
public interface GeneratedObject {

	/**
	 * Renders this object as one or more lines of Java source code.
	 *
	 * @return an ordered list of source lines, without trailing newlines
	 */
	ObjectArrayList<String> generateJava();

	/**
	 * Renders this object as one or more lines of TypeScript source code.
	 *
	 * @return an ordered list of source lines, without trailing newlines
	 */
	ObjectArrayList<String> generateTypeScript();
}
