package org.mtr.core.generator.schema;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generator.objects.Type;

import javax.annotation.Nullable;

public class TypeWithData {

	public final Type type;
	public final String readData;
	public final String unpackData;
	public final String writeData;
	public final String randomData;
	public final boolean requireAbstractInitializationMethod;
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

	public static TypeWithData createObject(String className, ObjectArrayList<String> extraParameters) {
		final ObjectArrayList<String> parameters = new ObjectArrayList<>();
		parameters.add("readerBaseChild");
		extraParameters.forEach(parameter -> parameters.add(String.format("%1$s%2$sParameter()", "%1$s", Utilities.capitalizeFirstLetter(parameter))));
		return new TypeWithData(
				Type.createObject(className, className),
				"%1$s = new %2$s(readerBase.getChild(\"%1$s\"));",
				String.format("readerBase.unpackChild(\"%1$s\", readerBaseChild -> %1$s = new %2$s(%3$s));", "%1$s", "%2$s", String.join(", ", parameters)),
				"if (%1$s != null) %1$s.serializeData(writerBase.writeChild(\"%1$s\"));",
				String.format("%1$s = TestUtilities.random%2$s();", "%1$s", className),
				true,
				extraParameters
		);
	}

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
