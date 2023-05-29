package org.mtr.core.data;

import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;

public interface SerializedDataBase {

	void updateData(ReaderBase readerBase);

	void serializeData(WriterBase writerBase);

	default void serializeFullData(WriterBase writerBase) {
		serializeData(writerBase);
	}
}
