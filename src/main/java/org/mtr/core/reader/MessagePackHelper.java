package org.mtr.core.reader;

import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.mtr.core.data.SerializedDataBase;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class MessagePackHelper extends ReaderBase<Value, MessagePackHelper> {

	public MessagePackHelper(Map<String, Value> map) {
		super(map);
	}

	private MessagePackHelper(Value value) {
		super(value);
	}

	@Override
	public boolean iterateReaderArray(String key, Consumer<MessagePackHelper> ifExists) {
		return iterateArray(key, MessagePackHelper::new, ifExists);
	}

	@Override
	public MessagePackHelper getChild(String key) {
		return getChild(key, MessagePackHelper::new);
	}

	@Override
	protected boolean getBoolean(Value value) {
		return value.asBooleanValue().getBoolean();
	}

	@Override
	protected int getInt(Value value) {
		return value.asIntegerValue().asInt();
	}

	@Override
	protected long getLong(Value value) {
		return value.asIntegerValue().asLong();
	}

	@Override
	protected double getDouble(Value value) {
		return value.asFloatValue().toDouble();
	}

	@Override
	protected String getString(Value value) {
		return value.asStringValue().asString();
	}

	@Override
	protected void iterateArray(Value value, Consumer<Value> consumer) {
		value.asArrayValue().forEach(consumer);
	}

	@Override
	protected void iterateMap(Value value, BiConsumer<String, Value> consumer) {
		value.asMapValue().entrySet().forEach(entry -> consumer.accept(entry.getKey().asStringValue().asString(), entry.getValue()));
	}

	public static void writeMessagePackDataset(MessagePacker messagePacker, Collection<? extends SerializedDataBase> dataSet, String key) throws IOException {
		messagePacker.packString(key);
		messagePacker.packArrayHeader(dataSet.size());
		for (final SerializedDataBase data : dataSet) {
			messagePacker.packMapHeader(data.messagePackLength());
			data.toMessagePack(messagePacker);
		}
	}
}
