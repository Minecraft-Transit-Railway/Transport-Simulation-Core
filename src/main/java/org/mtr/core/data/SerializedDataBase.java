package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.mtr.core.reader.ReaderBase;

import java.io.IOException;

public abstract class SerializedDataBase {

	public abstract <T extends ReaderBase<U, T>, U> void updateData(T readerBase);

	public abstract void toMessagePack(MessagePacker messagePacker) throws IOException;

	public abstract int messagePackLength();

	public abstract String getHexId();

	public void toFullMessagePack(MessagePacker messagePacker) throws IOException {
		toMessagePack(messagePacker);
	}

	public int fullMessagePackLength() {
		return messagePackLength();
	}
}
