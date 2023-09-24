package org.mtr.core.serializers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.mtr.core.Main;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class JsonReader extends ReaderBase {

	private final Object2ObjectArrayMap<String, JsonElement> map;

	public JsonReader(JsonElement value) {
		map = new Object2ObjectArrayMap<>();
		iterateMap(value, map::put);
	}

	private JsonReader(Object2ObjectArrayMap<String, JsonElement> map) {
		this.map = map;
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
	public void iterateBooleanArray(String key, Runnable clearList, BooleanConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getBoolean(arrayValue))));
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
	public void iterateIntArray(String key, Runnable clearList, IntConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getInt(arrayValue))));
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
	public void iterateLongArray(String key, Runnable clearList, LongConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getLong(arrayValue))));
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
	public void iterateDoubleArray(String key, Runnable clearList, DoubleConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getDouble(arrayValue))));
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
	public void iterateStringArray(String key, Runnable clearList, Consumer<String> ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getString(arrayValue))));
	}

	@Override
	public void iterateReaderArray(String key, Runnable clearList, Consumer<ReaderBase> ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(new JsonReader(arrayValue))));
	}

	@Override
	public ReaderBase getChild(String key) {
		return getOrDefault(key, new JsonReader(new JsonObject()), JsonReader::new);
	}

	@Override
	public void unpackChild(String key, Consumer<ReaderBase> ifExists) {
		unpack(key, value -> ifExists.accept(new JsonReader(value)));
	}

	@Override
	public void merge(ReaderBase readerBase) {
		if (readerBase instanceof JsonReader) {
			map.putAll(((JsonReader) readerBase).map);
		}
	}

	private boolean getBoolean(JsonElement value) {
		return value.getAsBoolean();
	}

	private int getInt(JsonElement value) {
		return value.getAsInt();
	}

	private long getLong(JsonElement value) {
		return value.getAsLong();
	}

	private double getDouble(JsonElement value) {
		return value.getAsDouble();
	}

	private String getString(JsonElement value) {
		return value.getAsString();
	}

	private void iterateArray(JsonElement value, Runnable clearList, Consumer<JsonElement> consumer) {
		clearList.run();
		value.getAsJsonArray().forEach(consumer);
	}

	private void iterateMap(JsonElement value, BiConsumer<String, JsonElement> consumer) {
		value.getAsJsonObject().asMap().forEach(consumer);
	}

	private void unpack(String key, Consumer<JsonElement> consumer) {
		unpackValue(map.get(key), consumer);
	}

	private <T> T getOrDefault(String key, T defaultValue, Function<JsonElement, T> function) {
		return getValueOrDefault(map.get(key), defaultValue, function);
	}

	public static JsonReader parse(String string) {
		try {
			return new JsonReader(JsonParser.parseString(string));
		} catch (Exception e) {
			Main.logException(e);
			return new JsonReader(new Object2ObjectArrayMap<>());
		}
	}
}