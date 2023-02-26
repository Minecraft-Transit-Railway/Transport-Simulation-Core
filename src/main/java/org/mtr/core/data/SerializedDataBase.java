package org.mtr.core.data;

import org.msgpack.core.MessagePacker;

import java.io.IOException;

public abstract class SerializedDataBase {

	public abstract void updateData(MessagePackHelper messagePackHelper);

	public abstract void toMessagePack(MessagePacker messagePacker) throws IOException;

	public abstract int messagePackLength();
}
