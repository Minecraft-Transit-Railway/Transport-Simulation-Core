package org.mtr.core.data;

import org.msgpack.core.MessagePacker;

import java.io.IOException;

public class Lift extends NameColorDataBase {

	public Lift(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);
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
