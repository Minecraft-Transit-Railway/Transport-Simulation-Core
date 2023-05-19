package org.mtr.core.data;

import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;

public abstract class SerializedDataBase {

	public abstract void updateData(ReaderBase readerBase);

	public abstract void serializeData(WriterBase writerBase);

	public abstract String getHexId();

	public void serializeFullData(WriterBase writerBase) {
		serializeData(writerBase);
	}
}
