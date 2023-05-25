package org.mtr.core.generator.schema;

import org.mtr.core.generator.objects.Type;

public class TypeWithData {

	public final Type type;
	public final String readData;
	public final String unpackData;
	public final String writeData;
	public final boolean requireAbstractInitializationMethod;
	public final boolean requireEnumInitialization;

	private TypeWithData(Type type, String readData, String unpackData, String writeData, boolean requireAbstractInitializationMethod, boolean requireEnumInitialization) {
		this.type = type;
		this.readData = readData;
		this.unpackData = unpackData;
		this.writeData = writeData;
		this.requireAbstractInitializationMethod = requireAbstractInitializationMethod;
		this.requireEnumInitialization = requireEnumInitialization;
	}

	public static TypeWithData createPrimitive(Type type, String primitiveType, String defaultValue) {
		return new TypeWithData(
				type,
				String.format("%1$s = readerBase.get%2$s(\"%1$s\", %3$s);", "%1$s", primitiveType, defaultValue),
				String.format("readerBase.unpack%2$s(\"%1$s\", value -> %1$s = value);", "%1$s", primitiveType),
				String.format("writerBase.write%2$s(\"%1$s\", %1$s);", "%1$s", primitiveType),
				false,
				false
		);
	}

	public static TypeWithData createPrimitiveArray(Type type, String arrayType) {
		return new TypeWithData(
				type,
				null,
				String.format("readerBase.iterate%2$sArray(\"%1$s\", %1$s::add);", "%1$s", arrayType),
				String.format("final WriterBase.Array %1$sWriterBaseArray = writerBase.writeArray(\"%1$s\"); %1$s.forEach(%1$sWriterBaseArray::write%2$s);", "%1$s", arrayType),
				false,
				false
		);
	}

	public static TypeWithData createArray(Type type, String arrayType) {
		return new TypeWithData(
				type,
				null,
				String.format("readerBase.iterateReaderArray(\"%1$s\", readerBaseChild -> %1$s.add(new %2$s(readerBaseChild)));", "%1$s", arrayType),
				"writerBase.writeDataset(%1$s, \"%1$s\");",
				false,
				false
		);
	}

	public static TypeWithData createObject(String className) {
		return new TypeWithData(
				Type.createObject(className),
				"%1$s = new %2$s(readerBase.getChild(\"%1$s\"));",
				"readerBase.unpackChild(\"%1$s\", readerBaseChild -> %1$s = new %2$s(readerBaseChild));",
				"%1$s.serializeData(writerBase.writeChild(\"%1$s\"));",
				true,
				false
		);
	}

	public static TypeWithData createEnum(String refName) {
		return new TypeWithData(
				Type.createEnum(refName),
				String.format("%1$s = EnumHelper.valueOf(%2$s.values()[0], readerBase.getString(\"%1$s\", \"\"));", "%1$s", refName),
				String.format("readerBase.unpackString(\"%1$s\", value -> %1$s = EnumHelper.valueOf(%2$s.values()[0], value));", "%1$s", refName),
				"writerBase.writeString(\"%1$s\", %1$s.toString());",
				false,
				true
		);
	}
}
