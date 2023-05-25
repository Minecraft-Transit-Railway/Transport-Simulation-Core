package org.mtr.core.generator.objects;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class Parameter extends Field {

	public Parameter(Type type, String name) {
		super(type, name, false);
	}

	@Override
	public ObjectArrayList<String> generate() {
		modifiers.clear();
		modifiers.add(Modifier.FINAL);
		return super.generate();
	}
}
