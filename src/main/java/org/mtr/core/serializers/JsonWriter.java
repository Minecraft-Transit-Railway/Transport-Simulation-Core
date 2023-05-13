package org.mtr.core.serializers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class JsonWriter extends WriterBase {

	private final JsonObject jsonObject;

	public JsonWriter(JsonObject jsonObject) {
		this.jsonObject = jsonObject;
	}

	@Override
	public void writeBoolean(String key, boolean value) {
		jsonObject.addProperty(key, value);
	}

	@Override
	public void writeInt(String key, int value) {
		jsonObject.addProperty(key, value);
	}

	@Override
	public void writeLong(String key, long value) {
		jsonObject.addProperty(key, value);
	}

	@Override
	public void writeDouble(String key, double value) {
		jsonObject.addProperty(key, value);
	}

	@Override
	public void writeString(String key, String value) {
		jsonObject.addProperty(key, value);
	}

	@Override
	public JsonArrayWriter writeArray(String key, int length) {
		final JsonArray jsonArray = new JsonArray();
		jsonObject.add(key, jsonArray);
		return new JsonArrayWriter(jsonArray);
	}

	@Override
	public WriterBase writeChild(String key, int length) {
		final JsonObject childObject = new JsonObject();
		jsonObject.add(key, childObject);
		return new JsonWriter(childObject);
	}

	private static final class JsonArrayWriter extends Array {

		private final JsonArray jsonArray;

		private JsonArrayWriter(JsonArray jsonArray) {
			this.jsonArray = jsonArray;
		}

		@Override
		public void writeBoolean(boolean value) {
			jsonArray.add(value);
		}

		@Override
		public void writeInt(int value) {
			jsonArray.add(value);
		}

		@Override
		public void writeLong(long value) {
			jsonArray.add(value);
		}

		@Override
		public void writeDouble(double value) {
			jsonArray.add(value);
		}

		@Override
		public void writeString(String value) {
			jsonArray.add(value);
		}

		@Override
		public WriterBase writeChild(int length) {
			final JsonObject childObject = new JsonObject();
			jsonArray.add(childObject);
			return new JsonWriter(childObject);
		}
	}
}
