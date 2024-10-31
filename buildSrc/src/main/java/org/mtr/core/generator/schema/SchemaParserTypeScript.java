package org.mtr.core.generator.schema;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generator.objects.Class;
import org.mtr.core.generator.objects.*;

import javax.annotation.Nullable;

public class SchemaParserTypeScript {

	private final Class schemaClass;
	private final Constructor constructor;
	private final String extendsClassName;
	final ObjectArrayList<String> testMethodContent1 = new ObjectArrayList<>();
	final ObjectArrayList<String> testMethodContent2 = new ObjectArrayList<>();

	public SchemaParserTypeScript(Class schemaClass, @Nullable String extendsClassName, JsonObject jsonObject) {
		this.schemaClass = schemaClass;
		constructor = schemaClass.createConstructor(VisibilityModifier.PUBLIC);
		this.extendsClassName = extendsClassName;
		final ObjectAVLTreeSet<String> imports = new ObjectAVLTreeSet<>();

		Utilities.iterateObject(jsonObject.getAsJsonObject("properties"), (key, propertyObject) -> {
			final TypeWithData typeWithData = SchemaParserJava.getType(propertyObject, false, imports);
			if (typeWithData != null) {
				final boolean required = Utilities.arrayContains(jsonObject.getAsJsonArray("required"), key);
				final boolean editable = jsonObject.has("typeScriptEditable") && jsonObject.get("typeScriptEditable").getAsBoolean();
				final Field field;

				if (typeWithData.type.isArray) {
					field = new Field(VisibilityModifier.PUBLIC, typeWithData.type, key, true);
					field.otherModifiers.add(OtherModifier.READONLY);
				} else {
					final String defaultValue = Utilities.getStringOrNull(propertyObject.get("default"));
					if (required) {
						field = new Field(VisibilityModifier.PUBLIC, typeWithData.type, key, false);

						if (!editable) {
							field.otherModifiers.add(OtherModifier.READONLY);
						}

						if (defaultValue == null) {
							constructor.parameters.add(new Parameter(typeWithData.type, key));
							constructor.content.add(String.format("this.%1$s = %1$s;", key));
						} else {
							constructor.content.add(String.format("this.%s%s;", key, typeWithData.type.getInitializerTypeScript(defaultValue, false)));
						}
					} else {
						final String newKey = key + "?";
						if (defaultValue == null) {
							field = new Field(VisibilityModifier.PUBLIC, typeWithData.type, newKey, false);
						} else {
							field = new Field(VisibilityModifier.PUBLIC, typeWithData.type, newKey, defaultValue);
						}
					}
				}

				schemaClass.fields.add(field);
			}
		});

		schemaClass.imports.addAll(imports);
	}

	public String generateSchemaClass(Object2ObjectAVLTreeMap<String, SchemaParserTypeScript> schemaParsers) {
		traverseExtendedClasses(this, schemaParsers);
		return String.join("\n", schemaClass.generateTypeScript());
	}

	private void traverseExtendedClasses(SchemaParserTypeScript schemaParserTypeScript, Object2ObjectAVLTreeMap<String, SchemaParserTypeScript> schemaParsers) {
		if (schemaParserTypeScript.extendsClassName != null) {
			final SchemaParserTypeScript extendedSchemaParserTypeScript = schemaParsers.get(schemaParserTypeScript.extendsClassName);
			constructor.superParameters.addAll(extendedSchemaParserTypeScript.constructor.parameters);
			testMethodContent2.addAll(extendedSchemaParserTypeScript.testMethodContent1);
			traverseExtendedClasses(extendedSchemaParserTypeScript, schemaParsers);
		}
	}
}
