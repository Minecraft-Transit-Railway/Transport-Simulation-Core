package org.mtr.core.data;

import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;

public abstract class SerializedDataBase {

	public abstract void updateData(ReaderBase readerBase);

	public abstract void toMessagePack(WriterBase writerBase);

	public abstract int messagePackLength();

	public abstract String getHexId();

	public void toFullMessagePack(WriterBase writerBase) {
		toMessagePack(writerBase);
	}

	public int fullMessagePackLength() {
		return messagePackLength();
	}
}
