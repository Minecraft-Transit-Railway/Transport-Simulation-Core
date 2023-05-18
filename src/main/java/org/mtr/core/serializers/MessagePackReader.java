package org.mtr.core.serializers;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class MessagePackReader extends ReaderBase {

	private final Object2ObjectArrayMap<String, Value> map;

	public MessagePackReader() {
		this.map = new Object2ObjectArrayMap<>();
	}

	public MessagePackReader(MessageUnpacker messageUnpacker) {
		map = new Object2ObjectArrayMap<>();
		try {
			final int size = messageUnpacker.unpackMapHeader();
			for (int i = 0; i < size; i++) {
				map.put(messageUnpacker.unpackString(), messageUnpacker.unpackValue());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private MessagePackReader(Value value) {
		map = new Object2ObjectArrayMap<>();
		iterateMap(value, map::put);
	}

	@Override
	public void unpackBoolean(String key, BooleanConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getBoolean(value)));
	}

	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		return getOrDefault(key, defaultValue, this::getBoolean);
	}

	@Override
	public void iterateBooleanArray(String key, BooleanConsumer ifExists) {
		unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(getBoolean(arrayValue))));
	}

	@Override
	public void unpackInt(String key, IntConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getInt(value)));
	}

	@Override
	public int getInt(String key, int defaultValue) {
		return getOrDefault(key, defaultValue, this::getInt);
	}

	@Override
	public void iterateIntArray(String key, IntConsumer ifExists) {
		unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(getInt(arrayValue))));
	}

	@Override
	public void unpackLong(String key, LongConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getLong(value)));
	}

	@Override
	public long getLong(String key, long defaultValue) {
		return getOrDefault(key, defaultValue, this::getLong);
	}

	@Override
	public void iterateLongArray(String key, LongConsumer ifExists) {
		unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(getLong(arrayValue))));
	}

	@Override
	public void unpackDouble(String key, DoubleConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getDouble(value)));
	}

	@Override
	public double getDouble(String key, double defaultValue) {
		return getOrDefault(key, defaultValue, this::getDouble);
	}

	@Override
	public void iterateDoubleArray(String key, DoubleConsumer ifExists) {
		unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(getDouble(arrayValue))));
	}

	@Override
	public void unpackString(String key, Consumer<String> ifExists) {
		unpack(key, value -> ifExists.accept(getString(value)));
	}

	@Override
	public String getString(String key, String defaultValue) {
		return getOrDefault(key, defaultValue, this::getString);
	}

	@Override
	public void iterateStringArray(String key, Consumer<String> ifExists) {
		unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(getString(arrayValue))));
	}

	@Override
	public boolean iterateReaderArray(String key, Consumer<ReaderBase> ifExists) {
		return unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(new MessagePackReader(arrayValue))));
	}

	@Override
	public void iterateKeys(String key, BiConsumer<String, ReaderBase> ifExists) {
		unpack(key, value -> {
			final MessagePackReader messagePackReader = new MessagePackReader(value);
			iterateMap(value, (mapKey, mapValue) -> ifExists.accept(mapKey, messagePackReader));
		});
	}

	private boolean getBoolean(Value value) {
		return value.asBooleanValue().getBoolean();
	}

	private int getInt(Value value) {
		return value.asIntegerValue().asInt();
	}

	private long getLong(Value value) {
		return value.asIntegerValue().asLong();
	}

	private double getDouble(Value value) {
		return value.asFloatValue().toDouble();
	}

	private String getString(Value value) {
		return value.asStringValue().asString();
	}

	private void iterateArray(Value value, Consumer<Value> consumer) {
		value.asArrayValue().forEach(consumer);
	}

	private void iterateMap(Value value, BiConsumer<String, Value> consumer) {
		value.asMapValue().entrySet().forEach(entry -> consumer.accept(getString(entry.getKey()), entry.getValue()));
	}

	private boolean unpack(String key, Consumer<Value> consumer) {
		return unpackValue(map.get(key), consumer);
	}

	private <T> T getOrDefault(String key, T defaultValue, Function<Value, T> function) {
		return getValueOrDefault(map.get(key), defaultValue, function);
	}
}
