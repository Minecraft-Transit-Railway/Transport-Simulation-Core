package org.mtr.core.serializer;

public interface SerializedDataBase {

	void updateData(ReaderBase readerBase);

	void serializeData(WriterBase writerBase);

	default void serializeFullData(WriterBase writerBase) {
		serializeData(writerBase);
	}
}
