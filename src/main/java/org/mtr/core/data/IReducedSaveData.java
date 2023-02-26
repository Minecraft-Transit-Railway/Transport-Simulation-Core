package org.mtr.core.data;

import org.msgpack.core.MessagePacker;

import java.io.IOException;

public interface IReducedSaveData {

	void toReducedMessagePack(MessagePacker messagePacker) throws IOException;

	int reducedMessagePackLength();
}
