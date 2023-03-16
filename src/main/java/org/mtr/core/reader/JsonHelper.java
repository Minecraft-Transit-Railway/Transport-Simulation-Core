package org.mtr.core.reader;

import com.google.gson.JsonElement;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class JsonHelper extends ReaderBase<JsonElement, JsonHelper> {

	public JsonHelper(Map<String, JsonElement> map) {
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
	public void iterateReaderMap(String key, Consumer<JsonHelper> ifExists) {
		iterateMap(key, JsonHelper::new, ifExists);
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
	protected float getFloat(JsonElement value) {
		return value.getAsFloat();
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
}
