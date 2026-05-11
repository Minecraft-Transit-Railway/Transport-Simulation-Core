package org.mtr.core.generator.schema;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.mtr.core.generator.objects.Type;

/**
 * Bundles a {@link Type} with the serialisation/deserialisation code snippets
 * needed to read, unpack, write, and randomly initialise a schema field of that type.
 *
 * <p>Instances are created exclusively through the static factory methods:
 * {@link #createPrimitive}, {@link #createPrimitiveArray}, {@link #createArray},
 * {@link #createObject}, and {@link #createEnum}.</p>
 */
public class TypeWithData {

	/**
	 * The resolved {@link Type} for this schema property.
	 */
	public final Type type;
	/**
	 * A {@link String#format} template used when reading a <em>required</em> field from
	 * a {@code ReaderBase} in the primary constructor. {@code %1$s} is the field name and
	 * {@code %2$s} is the Java type name.  {@code null} for array types (handled via
	 * {@link #unpackData} instead).
	 */
	public final String readData;
	/**
	 * A {@link String#format} template used inside {@code updateData(ReaderBase)} to
	 * unpack/iterate an optional or array field.  {@code %1$s} is the field name and
	 * {@code %2$s} is the Java type name.
	 */
	public final String unpackData;
	/**
	 * A {@link String#format} template used inside {@code serializeData(WriterBase)} to
	 * write the field.  {@code %1$s} is the field name and {@code %2$s} is the Java type
	 * name.
	 */
	public final String writeData;
	/**
	 * A {@link String#format} template used in generated test code to assign a random
	 * value to the field.  {@code %1$s} is the fully-qualified field access expression
	 * (e.g. {@code "data.myField"}).
	 */
	public final String randomData;
	/**
	 * {@code true} when the field's initialiser must be provided by an {@code abstract}
	 * method in the generated class (used for optional object-type fields).
	 */
	public final boolean requireAbstractInitializationMethod;
	/**
	 * Extra schema-defined parameter names that must also be forwarded to nested object
	 * constructors.
	 */
	public final ObjectArrayList<String> extraParameters;

	private TypeWithData(Type type, @Nullable String readData, String unpackData, String writeData, String randomData, boolean requireAbstractInitializationMethod, ObjectArrayList<String> extraParameters) {
		this.type = type;
		this.readData = readData;
		this.unpackData = unpackData;
		this.writeData = writeData;
		this.randomData = randomData;
		this.requireAbstractInitializationMethod = requireAbstractInitializationMethod;
		this.extraParameters = extraParameters;
	}

	/**
	 * Creates a {@code TypeWithData} for a scalar primitive field (boolean, integer,
	 * number, or string).
	 *
	 * @param type          the resolved primitive {@link Type}
	 * @param primitiveType the capitalised reader/writer method suffix (e.g. {@code "Boolean"}, {@code "Long"})
	 * @param defaultValue  the literal default value emitted in the primary constructor (e.g. {@code "0"}, {@code "\"\""})
	 * @return a new {@code TypeWithData} for the primitive
	 */
	public static TypeWithData createPrimitive(Type type, String primitiveType, String defaultValue) {
		return new TypeWithData(
			type,
			String.format("%1$s = readerBase.get%2$s(\"%1$s\", %3$s);", "%1$s", primitiveType, defaultValue),
			String.format("readerBase.unpack%2$s(\"%1$s\", value -> %1$s = value);", "%1$s", primitiveType),
			String.format("writerBase.write%2$s(\"%1$s\", %1$s);", "%1$s", primitiveType),
			String.format("%1$s = %2$s;", "%1$s", getRandomPrimitive(primitiveType)),
			false,
			ObjectArrayList.of()
		);
	}

	/**
	 * Creates a {@code TypeWithData} for a primitive array field (boolean[], integer[],
	 * number[], or string[]).
	 *
	 * @param type      the resolved array {@link Type}
	 * @param arrayType the capitalised reader/writer method suffix (e.g. {@code "Long"}, {@code "String"})
	 * @return a new {@code TypeWithData} for the primitive array
	 */
	public static TypeWithData createPrimitiveArray(Type type, String arrayType) {
		return new TypeWithData(
			type,
			null,
			String.format("readerBase.iterate%2$sArray(\"%1$s\", %1$s::clear, %1$s::add);", "%1$s", arrayType),
			String.format("final WriterBase.Array %1$sWriterBaseArray = writerBase.writeArray(\"%1$s\"); %1$s.forEach(%1$sWriterBaseArray::write%2$s);", "%1$s", arrayType),
			String.format("%1$s.clear(); TestUtilities.randomLoop(() -> %1$s.add(%2$s));", "%1$s", getRandomPrimitive(arrayType)),
			false,
			ObjectArrayList.of()
		);
	}

	/**
	 * Creates a {@code TypeWithData} for an array of schema objects.
	 *
	 * @param type            the resolved array {@link Type}
	 * @param arrayType       the simple Java class name of the element type
	 * @param extraParameters additional parameter names forwarded to each element constructor
	 * @return a new {@code TypeWithData} for the object array
	 */
	public static TypeWithData createArray(Type type, String arrayType, ObjectArrayList<String> extraParameters) {
		final ObjectArrayList<String> parameters = new ObjectArrayList<>();
		parameters.add("readerBaseChild");
		extraParameters.forEach(parameter -> parameters.add(String.format("%1$s%2$sParameter()", "%1$s", Utilities.capitalizeFirstLetter(parameter))));
		return new TypeWithData(
			type,
			null,
			String.format("readerBase.iterateReaderArray(\"%1$s\", %1$s::clear, readerBaseChild -> %1$s.add(new %2$s(%3$s)));", "%1$s", arrayType, String.join(", ", parameters)),
			"writerBase.writeDataset(%1$s, \"%1$s\");",
			String.format("%1$s.clear(); TestUtilities.randomLoop(() -> %1$s.add(TestUtilities.random%2$s()));", "%1$s", arrayType),
			false,
			extraParameters
		);
	}

	/**
	 * Creates a {@code TypeWithData} for a single nested schema object field.
	 *
	 * @param className       the simple Java class name of the nested object
	 * @param extraParameters additional parameter names forwarded to the nested object constructor
	 * @return a new {@code TypeWithData} for the nested object
	 */
	public static TypeWithData createObject(String className, ObjectArrayList<String> extraParameters) {
		final ObjectArrayList<String> parameters = new ObjectArrayList<>();
		parameters.add("readerBaseChild");
		extraParameters.forEach(parameter -> parameters.add(String.format("%1$s%2$sParameter()", "%1$s", Utilities.capitalizeFirstLetter(parameter))));
		return new TypeWithData(
			Type.createObject(className, className + "DTO"),
			"%1$s = new %2$s(readerBase.getChild(\"%1$s\"));",
			String.format("readerBase.unpackChild(\"%1$s\", readerBaseChild -> %1$s = new %2$s(%3$s));", "%1$s", "%2$s", String.join(", ", parameters)),
			"if (%1$s != null) { %1$s.serializeData(writerBase.writeChild(\"%1$s\")); }",
			String.format("%1$s = TestUtilities.random%2$s();", "%1$s", className),
			true,
			extraParameters
		);
	}

	/**
	 * Creates a {@code TypeWithData} for a Java enum / TypeScript union-of-string-literals field.
	 *
	 * @param refName        the Java enum class name
	 * @param typeScriptEnum the TypeScript enum string (pipe-separated), or {@code null} if not applicable
	 * @return a new {@code TypeWithData} for the enum
	 */
	public static TypeWithData createEnum(String refName, @Nullable String typeScriptEnum) {
		return new TypeWithData(
			Type.createEnum(refName, typeScriptEnum == null ? "" : typeScriptEnum),
			String.format("%1$s = EnumHelper.valueOf(%2$s.values()[0], readerBase.getString(\"%1$s\", \"\"));", "%1$s", refName),
			String.format("readerBase.unpackString(\"%1$s\", value -> %1$s = EnumHelper.valueOf(%2$s.values()[0], value));", "%1$s", refName),
			"writerBase.writeString(\"%1$s\", %1$s.toString());",
			String.format("%1$s = TestUtilities.randomEnum(%2$s.values());", "%1$s", refName),
			false,
			ObjectArrayList.of()
		);
	}

	private static String getRandomPrimitive(String primitiveType) {
		if (primitiveType.equals(Type.STRING.nameJava)) {
			return "TestUtilities.randomString()";
		} else {
			return String.format("RANDOM.next%s()", primitiveType);
		}
	}
}
