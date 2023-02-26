package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class MessagePackHelper {

	private final Map<String, Value> map;

	public MessagePackHelper(Map<String, Value> map) {
		this.map = map;
	}

	public Value get(String key) {
		return map.get(key);
	}

	public void unpackBoolean(String key, Consumer<Boolean> ifExists) {
		unpackBoolean(key, ifExists, null);
	}

	public void unpackBoolean(String key, Consumer<Boolean> ifExists, Runnable ifNotExists) {
		unpack(key, value -> value.asBooleanValue().getBoolean(), ifExists, ifNotExists);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return getOrDefault(key, defaultValue, value -> value.asBooleanValue().getBoolean());
	}

	public void unpackInt(String key, Consumer<Integer> ifExists) {
		unpackInt(key, ifExists, null);
	}

	public void unpackInt(String key, Consumer<Integer> ifExists, Runnable ifNotExists) {
		unpack(key, value -> value.asIntegerValue().asInt(), ifExists, ifNotExists);
	}

	public int getInt(String key, int defaultValue) {
		return getOrDefault(key, defaultValue, value -> value.asIntegerValue().asInt());
	}

	public void unpackLong(String key, Consumer<Long> ifExists) {
		unpackLong(key, ifExists, null);
	}

	public void unpackLong(String key, Consumer<Long> ifExists, Runnable ifNotExists) {
		unpack(key, value -> value.asIntegerValue().asLong(), ifExists, ifNotExists);
	}

	public long getLong(String key, long defaultValue) {
		return getOrDefault(key, defaultValue, value -> value.asIntegerValue().asLong());
	}

	public void unpackFloat(String key, Consumer<Float> ifExists) {
		unpackFloat(key, ifExists, null);
	}

	public void unpackFloat(String key, Consumer<Float> ifExists, Runnable ifNotExists) {
		unpack(key, value -> value.asFloatValue().toFloat(), ifExists, ifNotExists);
	}

	public float getFloat(String key, float defaultValue) {
		return getOrDefault(key, defaultValue, value -> value.asFloatValue().toFloat());
	}

	public void unpackDouble(String key, Consumer<Double> ifExists) {
		unpackDouble(key, ifExists, null);
	}

	public void unpackDouble(String key, Consumer<Double> ifExists, Runnable ifNotExists) {
		unpack(key, value -> value.asFloatValue().toDouble(), ifExists, ifNotExists);
	}

	public double getDouble(String key, double defaultValue) {
		return getOrDefault(key, defaultValue, value -> value.asFloatValue().toDouble());
	}

	public void unpackString(String key, Consumer<String> ifExists) {
		unpackString(key, ifExists, null);
	}

	public void unpackString(String key, Consumer<String> ifExists, Runnable ifNotExists) {
		unpack(key, value -> value.asStringValue().asString(), ifExists, ifNotExists);
	}

	public String getString(String key, String defaultValue) {
		return getOrDefault(key, defaultValue, value -> value.asStringValue().asString());
	}

	public void iterateArrayValue(String key, Consumer<Value> consumer) {
		if (map.containsKey(key)) {
			map.get(key).asArrayValue().forEach(consumer);
		}
	}

	public void iterateMapValue(String key, Consumer<Map.Entry<Value, Value>> consumer) {
		if (map.containsKey(key)) {
			map.get(key).asMapValue().entrySet().forEach(consumer);
		}
	}

	private <T> void unpack(String key, Function<Value, T> function, Consumer<T> ifExists, Runnable ifNotExists) {
		if (map.containsKey(key)) {
			ifExists.accept(function.apply(map.get(key)));
		} else if (ifNotExists != null) {
			ifNotExists.run();
		}
	}

	private <T> T getOrDefault(String key, T defaultValue, Function<Value, T> function) {
		return map.containsKey(key) ? function.apply(map.get(key)) : defaultValue;
	}

	public static void writeMessagePackDataset(MessagePacker messagePacker, Collection<? extends SerializedDataBase> dataSet, String key) throws IOException {
		messagePacker.packString(key);
		messagePacker.packArrayHeader(dataSet.size());
		for (final SerializedDataBase data : dataSet) {
			messagePacker.packMapHeader(data.messagePackLength());
			data.toMessagePack(messagePacker);
		}
	}

	public static Map<String, Value> castMessagePackValueToSKMap(Value value) {
		final Map<Value, Value> oldMap = value == null ? new HashMap<>() : value.asMapValue().map();
		final HashMap<String, Value> resultMap = new HashMap<>(oldMap.size());
		oldMap.forEach((key, newValue) -> resultMap.put(key.asStringValue().asString(), newValue));
		return resultMap;
	}
}
