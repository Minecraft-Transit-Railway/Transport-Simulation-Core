package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.mtr.core.reader.MessagePackHelper;
import org.mtr.core.reader.ReaderBase;

import java.io.IOException;

public class Lift extends NameColorDataBase {

	public Lift(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		super.updateData(readerBase);
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength();
	}

	@Override
	protected boolean hasTransportMode() {
		return false;
	}
}
