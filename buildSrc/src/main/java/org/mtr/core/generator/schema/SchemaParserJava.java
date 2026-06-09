package org.mtr.core.generator.schema;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.mtr.core.generator.objects.*;
import org.mtr.core.generator.objects.Class;

/**
 * Parses a single JSON schema object into a Java code-model ({@link Class}).
 *
 * <p>Construction inspects the {@code properties}, {@code required},
 * {@code javaConstructorFields}, and {@code javaImplements} keys of the supplied
 * {@link JsonObject} and populates the class model accordingly.  When all sibling
 * schema parsers are available, call {@link #generateSchemaClass} to finalise
 * inheritance wiring and obtain the rendered Java source.</p>
 */
public class SchemaParserJava {

	private final Class schemaClass;
	private final Constructor constructor1;
	private final Constructor constructor2;
	private final Method updateMethod;
	private final Method serializeMethod;
	@Nullable
	private final String extendsClassName;

	/**
	 * Creates a schema parser for one JSON schema object.
	 *
	 * @param schemaClass      the class model to populate
	 * @param extendsClassName the {@code Schema}-suffixed name of the direct superclass,
	 *                         or {@code null} if the class has no schema parent
	 * @param jsonObject       the raw JSON schema object
	 */
	public SchemaParserJava(Class schemaClass, @Nullable String extendsClassName, JsonObject jsonObject) {
		this.schemaClass = schemaClass;
		constructor1 = schemaClass.createConstructor(VisibilityModifier.PROTECTED);
		constructor2 = schemaClass.createConstructor(VisibilityModifier.PROTECTED);
		updateMethod = new Method(VisibilityModifier.PUBLIC, null, "updateData");
		serializeMethod = new Method(VisibilityModifier.PUBLIC, null, "serializeData");
		this.extendsClassName = extendsClassName;

		updateMethod.parameters.add(new Parameter(Type.createObject("ReaderBase", ""), "readerBase"));
		serializeMethod.parameters.add(new Parameter(Type.createObject("WriterBase", ""), "writerBase"));
		schemaClass.methods.add(updateMethod);
		schemaClass.methods.add(serializeMethod);
		final Method toStringMethod = new Method(VisibilityModifier.PUBLIC, Type.STRING, "toString");
		toStringMethod.content.add(extendsClassName == null ? "return \"\"" : "return super.toString()");
		schemaClass.methods.add(toStringMethod);

		Utilities.iterateObject(jsonObject.getAsJsonObject("properties"), (key, propertyObject) -> {
			final TypeWithData typeWithData = getType(propertyObject, false, new ObjectAVLTreeSet<>());
			if (typeWithData != null) {
				final boolean required = Utilities.arrayContains(jsonObject.getAsJsonArray("required"), key);
				final Field field;

				if (typeWithData.type.isArray) {
					field = new Field(VisibilityModifier.PROTECTED, typeWithData.type, key, true);
					field.otherModifiers.add(OtherModifier.FINAL);
					addNonFinalSerialization(typeWithData, key);
				} else {
					final String defaultValue = Utilities.getStringOrNull(propertyObject.get("default"));
					if (required) {
						field = new Field(VisibilityModifier.PROTECTED, typeWithData.type, key, false);
						field.otherModifiers.add(OtherModifier.FINAL);

						if (defaultValue == null) {
							constructor1.parameters.add(new Parameter(typeWithData.type, key));
							constructor1.content.add(String.format("this.%1$s = %1$s;", key));
						} else {
							constructor1.content.add(String.format("this.%s%s;", key, typeWithData.type.getInitializerJava(defaultValue, false)));
						}

						if (typeWithData.readData != null) {
							// typeWithData.readData should never be null
							constructor2.content.add(String.format(typeWithData.readData, key, typeWithData.type.nameJava));
						}

						serializeMethod.content.add(String.format(typeWithData.writeData, key, typeWithData.type.nameJava));
					} else {
						if (defaultValue != null) {
							field = new Field(VisibilityModifier.PROTECTED, typeWithData.type, key, defaultValue);
						} else if (typeWithData.requireAbstractInitializationMethod) {
							final String methodName = String.format("getDefault%s", Utilities.capitalizeFirstLetter(key));
							field = new Field(VisibilityModifier.PROTECTED, typeWithData.type, key, String.format("%s()", methodName));
							field.annotations.add("Nullable");
							final Method method1 = new Method(VisibilityModifier.PROTECTED, typeWithData.type, methodName);
							method1.otherModifiers.add(OtherModifier.ABSTRACT);
							method1.annotations.add("Nullable");
							schemaClass.methods.add(method1);
						} else {
							field = new Field(VisibilityModifier.PROTECTED, typeWithData.type, key, true);
						}

						addNonFinalSerialization(typeWithData, key);
					}
				}

				typeWithData.extraParameters.forEach(parameter -> {
					final String methodName = String.format("%s%sParameter", key, Utilities.capitalizeFirstLetter(parameter));
					final Method method = new Method(VisibilityModifier.PROTECTED, Type.createObject(Utilities.capitalizeFirstLetter(parameter), ""), methodName);
					method.otherModifiers.add(OtherModifier.ABSTRACT);
					schemaClass.methods.add(method);
				});

				schemaClass.fields.add(field);
				toStringMethod.content.add(String.format("\t+ \"%1$s: \" + %1$s + \"\\n\"", key));
			}
		});

		Utilities.iterateStringArray(jsonObject.getAsJsonArray("javaConstructorFields"), constructorField -> {
			final Type type = Type.createObject(Utilities.capitalizeFirstLetter(constructorField), "");
			constructor1.parameters.add(new Parameter(type, constructorField));
			constructor1.content.add(String.format("this.%1$s = %1$s;", constructorField));
			constructor2.parameters.add(new Parameter(type, constructorField));
			constructor2.content.add(String.format("this.%1$s = %1$s;", constructorField));
			final Field field = new Field(VisibilityModifier.PROTECTED, type, constructorField, false);
			field.otherModifiers.add(OtherModifier.FINAL);
			schemaClass.fields.add(field);
		});

		Utilities.iterateStringArray(jsonObject.getAsJsonArray("javaImplements"), schemaClass.implementsClasses::add);
		toStringMethod.content.add(";");

		if (extendsClassName == null) {
			constructor2.parameters.add(0, new Parameter(Type.createObject("ReaderBase", ""), "readerBase"));
		}
	}

	/**
	 * Finalises the class model (wires super-constructor parameters) and renders the
	 * class to a Java source string.
	 *
	 * @param schemaParsers all sibling schema parsers keyed by schema class name, used to
	 *                      resolve the inheritance chain
	 * @return the complete Java source text for the generated schema class
	 */
	public String generateSchemaClass(Object2ObjectAVLTreeMap<String, SchemaParserJava> schemaParsers) {
		if (extendsClassName != null) {
			updateMethod.content.add(0, "super.updateData(readerBase);");
			serializeMethod.content.add(0, "super.serializeData(writerBase);");
		}

		traverseExtendedClasses(this, schemaParsers);

		return String.join("\n", schemaClass.generateJava());
	}

	private void addNonFinalSerialization(TypeWithData typeWithData, String key) {
		final String methodName = String.format("serialize%s", Utilities.capitalizeFirstLetter(key));
		final Method method = new Method(VisibilityModifier.PROTECTED, null, methodName);
		method.parameters.add(new Parameter(Type.createObject("WriterBase", ""), "writerBase"));
		method.content.add(String.format(typeWithData.writeData, key, typeWithData.type.nameJava));
		schemaClass.methods.add(method);
		updateMethod.content.add(String.format(typeWithData.unpackData, key, typeWithData.type.nameJava));
		serializeMethod.content.add(String.format("%s(writerBase);", methodName));
	}

	private void traverseExtendedClasses(SchemaParserJava schemaParserJava, Object2ObjectAVLTreeMap<String, SchemaParserJava> schemaParsers) {
		if (schemaParserJava.extendsClassName != null) {
			final SchemaParserJava extendedSchemaParserJava = schemaParsers.get(schemaParserJava.extendsClassName);
			constructor1.superParameters.addAll(extendedSchemaParserJava.constructor1.parameters);
			constructor2.superParameters.addAll(extendedSchemaParserJava.constructor2.parameters);
			traverseExtendedClasses(extendedSchemaParserJava, schemaParsers);
		}
	}

	/**
	 * Resolves the {@link TypeWithData} for a single JSON schema property definition.
	 *
	 * <p>Handles {@code $ref} (object and enum references), primitive types
	 * ({@code boolean}, {@code integer}, {@code number}, {@code string}), and
	 * recursive {@code array} items.  Returns {@code null} for unsupported property
	 * shapes (e.g. nested objects).</p>
	 *
	 * @param jsonObject        the property's JSON schema node
	 * @param isArray           {@code true} when this call is processing an {@code items} sub-node
	 * @param typeScriptImports mutable set that collects TypeScript import names for object references
	 * @return the resolved {@link TypeWithData}, or {@code null} if the property is unsupported
	 */
	@Nullable
	public static TypeWithData getType(JsonObject jsonObject, boolean isArray, ObjectAVLTreeSet<String> typeScriptImports) {
		final String refName = Utilities.getStringOrNull(jsonObject.get("$ref"));
		final String typeString = Utilities.getStringOrNull(jsonObject.get("type"));

		if (refName != null) {
			final String formattedRefName = Utilities.formatRefName(refName);
			if (Utilities.isObject(refName)) {
				typeScriptImports.add(Utilities.formatRefNameRaw(refName));
				final ObjectArrayList<String> extraParameters = new ObjectArrayList<>();
				Utilities.iterateStringArray(jsonObject.getAsJsonArray("parameters"), parameter -> extraParameters.add(Utilities.formatRefName(parameter)));
				return isArray ? TypeWithData.createArray(Type.createArray(formattedRefName, formattedRefName + "DTO"), formattedRefName, extraParameters) : TypeWithData.createObject(formattedRefName, extraParameters);
			} else {
				return isArray ? null : TypeWithData.createEnum(formattedRefName, Utilities.getStringOrNull(jsonObject.get("typeScriptEnum")));
			}
		} else if (typeString != null) {
			return switch (typeString) {
				case "boolean" -> isArray ? TypeWithData.createPrimitiveArray(Type.BOOLEAN_ARRAY, "Boolean") : TypeWithData.createPrimitive(Type.BOOLEAN, "Boolean", "false");
				case "integer" -> isArray ? TypeWithData.createPrimitiveArray(Type.INTEGER_ARRAY, "Long") : TypeWithData.createPrimitive(Type.INTEGER, "Long", "0");
				case "number" -> isArray ? TypeWithData.createPrimitiveArray(Type.NUMBER_ARRAY, "Double") : TypeWithData.createPrimitive(Type.NUMBER, "Double", "0");
				case "string" -> isArray ? TypeWithData.createPrimitiveArray(Type.STRING_ARRAY, "String") : TypeWithData.createPrimitive(Type.STRING, "String", "\"\"");
				case "array" -> getType(jsonObject.getAsJsonObject("items"), true, typeScriptImports);
				// TODO nested objects ("object") not yet supported.
				default -> null;
			};
		} else {
			return null;
		}
	}
}
