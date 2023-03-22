package org.mtr.core.reader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class JsonHelper extends ReaderBase<JsonElement, JsonHelper> {

	public JsonHelper(Object2ObjectArrayMap<String, JsonElement> map) {
		super(map);
	}

	private JsonHelper(JsonElement value) {
		super(value);
	}

	@Override
	public boolean iterateReaderArray(String key, Consumer<JsonHelper> ifExists) {
		return iterateArray(key, JsonHelper::new, ifExists);
	}

	@Override
	public JsonHelper getChild(String key) {
		return getChild(key, JsonHelper::new);
	}

	@Override
	protected boolean getBoolean(JsonElement value) {
		return value.getAsBoolean();
	}

	@Override
	protected int getInt(JsonElement value) {
		return value.getAsInt();
	}

	@Override
	protected long getLong(JsonElement value) {
		return value.getAsLong();
	}

	@Override
	protected double getDouble(JsonElement value) {
		return value.getAsDouble();
	}

	@Override
	protected String getString(JsonElement value) {
		return value.getAsString();
	}

	@Override
	protected void iterateArray(JsonElement value, Consumer<JsonElement> consumer) {
		value.getAsJsonArray().forEach(consumer);
	}

	@Override
	protected void iterateMap(JsonElement value, BiConsumer<String, JsonElement> consumer) {
		value.getAsJsonObject().asMap().forEach(consumer);
	}

	public static JsonHelper parse(String string) {
		try {
			return new JsonHelper(JsonParser.parseString(string));
		} catch (Exception e) {
			e.printStackTrace();
			return new JsonHelper(new Object2ObjectArrayMap<>());
		}
	}
}
