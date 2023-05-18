package org.mtr.core.serializers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

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
		return unpack(key, value -> iterateArray(value, arrayValue -> ifExists.accept(new JsonReader(arrayValue))));
	}

	@Override
	public void iterateKeys(String key, BiConsumer<String, ReaderBase> ifExists) {
		unpack(key, value -> {
			final JsonReader jsonReader = new JsonReader(value);
			iterateMap(value, (mapKey, mapValue) -> ifExists.accept(mapKey, jsonReader));
		});
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

	private void iterateArray(JsonElement value, Consumer<JsonElement> consumer) {
		value.getAsJsonArray().forEach(consumer);
	}

	private void iterateMap(JsonElement value, BiConsumer<String, JsonElement> consumer) {
		value.getAsJsonObject().asMap().forEach(consumer);
	}

	private boolean unpack(String key, Consumer<JsonElement> consumer) {
		return unpackValue(map.get(key), consumer);
	}

	private <T> T getOrDefault(String key, T defaultValue, Function<JsonElement, T> function) {
		return getValueOrDefault(map.get(key), defaultValue, function);
	}

	public static JsonReader parse(String string) {
		try {
			return new JsonReader(JsonParser.parseString(string));
		} catch (Exception e) {
			e.printStackTrace();
			return new JsonReader(new Object2ObjectArrayMap<>());
		}
	}
}
