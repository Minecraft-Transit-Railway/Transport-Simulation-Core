package org.mtr.core.generator.schema;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.generator.objects.Class;
import org.mtr.core.generator.objects.*;

import java.util.stream.Collectors;

public class SchemaParser {

	private final Class generatedClass;
	private final Method constructor1;
	private final Method constructor2;
	private final Method updateMethod;
	private final Method serializeMethod;
	private final String extendsClassName;
	private final ObjectArrayList<ObjectObjectImmutablePair<Type, String>> constructor1Parameters = new ObjectArrayList<>();
	private final ObjectArrayList<ObjectObjectImmutablePair<Type, String>> constructor2Parameters = new ObjectArrayList<>();

	public SchemaParser(String name, String packageName, JsonObject jsonObject) {
		generatedClass = new Class(name, Utilities.getStringOrNull(jsonObject.get("javaExtends")), packageName);
		constructor1 = Method.createConstructor(name);
		constructor2 = Method.createConstructor(name);
		updateMethod = Method.createMethod("updateData", null);
		serializeMethod = Method.createMethod("serializeData", null);
		final JsonObject extendsObject = jsonObject.getAsJsonObject("extends");
		extendsClassName = extendsObject == null ? null : Utilities.formatClassName(extendsObject.get("$ref").getAsString());

		generatedClass.imports.add("org.mtr.core.data.*");
		generatedClass.imports.add("org.mtr.core.serializers.*");
		generatedClass.imports.add("org.mtr.core.simulation.*");
		generatedClass.imports.add("org.mtr.core.tools.*");
		constructor1.modifiers.add(Modifier.PROTECTED);
		constructor2.parameters.add(new Parameter(Type.createObject("ReaderBase"), "readerBase"));
		constructor2.modifiers.add(Modifier.PROTECTED);
		updateMethod.parameters.add(new Parameter(Type.createObject("ReaderBase"), "readerBase"));
		updateMethod.modifiers.add(Modifier.PUBLIC);
		serializeMethod.parameters.add(new Parameter(Type.createObject("WriterBase"), "writerBase"));
		serializeMethod.modifiers.add(Modifier.PUBLIC);
		generatedClass.methods.add(constructor1);
		generatedClass.methods.add(constructor2);
		generatedClass.methods.add(updateMethod);
		generatedClass.methods.add(serializeMethod);

		Utilities.iterateObject(jsonObject.getAsJsonObject("properties"), (key, propertyObject) -> {
			final TypeWithData typeWithData = getType(propertyObject, false);
			if (typeWithData != null) {
				final boolean required = Utilities.arrayContains(jsonObject.getAsJsonArray("required"), key);
				final Field field;

				if (typeWithData.type.isArray) {
					field = new Field(typeWithData.type, key, true);
					field.modifiers.add(Modifier.FINAL);
					addNonFinalSerialization(typeWithData, key);
				} else {
					final String defaultValue = Utilities.getStringOrNull(propertyObject.get("default"));
					if (required) {
						field = new Field(typeWithData.type, key, false);
						field.modifiers.add(Modifier.FINAL);
						addConstructorSerialization(typeWithData, key, defaultValue == null ? null : typeWithData.type.getInitializer(defaultValue, false));
					} else {
						if (defaultValue != null) {
							field = new Field(typeWithData.type, key, defaultValue);
						} else if (typeWithData.requireAbstractInitializationMethod) {
							final String methodName = String.format("getDefault%s", Utilities.capitalizeFirstLetter(key));
							field = new Field(typeWithData.type, key, String.format("%s()", methodName));
							final Method method = Method.createMethod(methodName, typeWithData.type);
							method.modifiers.add(Modifier.PROTECTED);
							method.modifiers.add(Modifier.ABSTRACT);
							generatedClass.methods.add(method);
						} else if (typeWithData.requireEnumInitialization) {
							field = new Field(typeWithData.type, key, String.format("%s.values()[0]", typeWithData.type.name));
						} else {
							field = new Field(typeWithData.type, key, true);
						}
						addNonFinalSerialization(typeWithData, key);
					}
				}

				field.modifiers.add(Modifier.PROTECTED);
				generatedClass.fields.add(field);
			}
		});

		Utilities.iterateStringArray(jsonObject.getAsJsonArray("javaConstructorFields"), constructorField -> {
			final Type type = Type.createObject(Utilities.capitalizeFirstLetter(constructorField));
			constructor1.parameters.add(new Parameter(type, constructorField));
			constructor1Parameters.add(new ObjectObjectImmutablePair<>(type, constructorField));
			constructor1.content.add(String.format("this.%1$s = %1$s;", constructorField));
			constructor2.parameters.add(new Parameter(type, constructorField));
			constructor2Parameters.add(new ObjectObjectImmutablePair<>(type, constructorField));
			constructor2.content.add(String.format("this.%1$s = %1$s;", constructorField));
			final Field field = new Field(type, constructorField, false);
			field.modifiers.add(Modifier.PROTECTED);
			field.modifiers.add(Modifier.FINAL);
			generatedClass.fields.add(field);
		});

		Utilities.iterateStringArray(jsonObject.getAsJsonArray("javaImplements"), generatedClass.implementsClasses::add);
	}

	public String generate(Object2ObjectAVLTreeMap<String, SchemaParser> schemaParsers) {
		final ObjectArrayList<ObjectObjectImmutablePair<Type, String>> extraConstructor1Parameters = new ObjectArrayList<>();
		final ObjectArrayList<ObjectObjectImmutablePair<Type, String>> extraConstructor2Parameters = new ObjectArrayList<>();
		traverseExtendedClasses(this, schemaParsers, extraConstructor1Parameters, extraConstructor2Parameters);

		if (extendsClassName != null) {
			extraConstructor1Parameters.forEach(parameter -> constructor1.parameters.add(new Parameter(parameter.left(), parameter.right())));
			extraConstructor2Parameters.forEach(parameter -> constructor2.parameters.add(new Parameter(parameter.left(), parameter.right())));
			constructor1.content.add(0, String.format("super(%s);", extraConstructor1Parameters.stream().map(ObjectObjectImmutablePair::right).collect(Collectors.joining(", "))));
			constructor2.content.add(0, String.format("super(readerBase, %s);", extraConstructor2Parameters.stream().map(ObjectObjectImmutablePair::right).collect(Collectors.joining(", "))));
			updateMethod.content.add(0, "super.updateData(readerBase);");
			serializeMethod.content.add(0, "super.serializeData(writerBase);");
		}

		return String.join("\n", generatedClass.generate());
	}

	private TypeWithData getType(JsonObject jsonObject, boolean isArray) {
		final String refName = Utilities.getStringOrNull(jsonObject.get("$ref"));
		final String typeString = Utilities.getStringOrNull(jsonObject.get("type"));

		if (refName != null) {
			final String formattedRefName = Utilities.formatRefName(refName);
			if (Utilities.isObject(refName)) {
				return isArray ? TypeWithData.createArray(Type.createArray(formattedRefName), formattedRefName) : TypeWithData.createObject(formattedRefName);
			} else {
				return isArray ? null : TypeWithData.createEnum(formattedRefName);
			}
		} else if (typeString != null) {
			switch (typeString) {
				case "boolean":
					return isArray ? TypeWithData.createPrimitiveArray(Type.BOOLEAN_ARRAY, "Boolean") : TypeWithData.createPrimitive(Type.BOOLEAN, "Boolean", "false");
				case "integer":
					return isArray ? TypeWithData.createPrimitiveArray(Type.INTEGER_ARRAY, "Long") : TypeWithData.createPrimitive(Type.INTEGER, "Long", "0");
				case "number":
					return isArray ? TypeWithData.createPrimitiveArray(Type.NUMBER_ARRAY, "Double") : TypeWithData.createPrimitive(Type.NUMBER, "Double", "0");
				case "string":
					return isArray ? TypeWithData.createPrimitiveArray(Type.STRING_ARRAY, "String") : TypeWithData.createPrimitive(Type.STRING, "String", "\"\"");
				case "array":
					return getType(jsonObject.getAsJsonObject("items"), true);
				case "object":
					// TODO nested objects not supported
				default:
					return null;
			}
		} else {
			return null;
		}
	}

	private void addNonFinalSerialization(TypeWithData typeWithData, String key) {
		final String methodName = String.format("serialize%s", Utilities.capitalizeFirstLetter(key));
		final Method method = Method.createMethod(methodName, null);
		method.parameters.add(new Parameter(Type.createObject("WriterBase"), "writerBase"));
		method.modifiers.add(Modifier.PROTECTED);
		method.content.add(String.format(typeWithData.writeData, key, typeWithData.type.name));
		generatedClass.methods.add(method);
		updateMethod.content.add(String.format(typeWithData.unpackData, key, typeWithData.type.name));
		serializeMethod.content.add(String.format("%s(writerBase);", methodName));
	}

	private void addConstructorSerialization(TypeWithData typeWithData, String key, String initializer) {
		if (initializer == null) {
			constructor1.parameters.add(new Parameter(typeWithData.type, key));
			constructor1Parameters.add(new ObjectObjectImmutablePair<>(typeWithData.type, key));
		}

		constructor1.content.add(String.format("this.%s%s;", key, initializer == null ? String.format(" = %s", key) : initializer));
		constructor2.content.add(String.format(typeWithData.readData, key, typeWithData.type.name));
		serializeMethod.content.add(String.format(typeWithData.writeData, key, typeWithData.type.name));
	}

	private static void traverseExtendedClasses(SchemaParser schemaParser, Object2ObjectAVLTreeMap<String, SchemaParser> schemaParsers, ObjectArrayList<ObjectObjectImmutablePair<Type, String>> extraConstructor1Parameters, ObjectArrayList<ObjectObjectImmutablePair<Type, String>> extraConstructor2Parameters) {
		if (schemaParser.extendsClassName != null) {
			final SchemaParser extendedSchemaParser = schemaParsers.get(schemaParser.extendsClassName);
			extraConstructor1Parameters.addAll(extendedSchemaParser.constructor1Parameters);
			extraConstructor2Parameters.addAll(extendedSchemaParser.constructor2Parameters);
			traverseExtendedClasses(extendedSchemaParser, schemaParsers, extraConstructor1Parameters, extraConstructor2Parameters);
		}
	}
}
