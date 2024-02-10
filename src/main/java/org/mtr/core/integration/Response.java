package org.mtr.core.integration;

import org.mtr.core.generated.integration.ResponseSchema;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.function.Function;

public final class Response extends ResponseSchema {

	public final JsonObject data;

	public Response(int code, long currentTime, String text, @Nullable JsonObject data) {
		super(code, currentTime, text, 1);
		this.data = data;
	}

	private Response(ReaderBase readerBase, JsonObject jsonObject) {
		super(readerBase);
		updateData(readerBase);
		data = jsonObject.getAsJsonObject("data");
	}

	public JsonObject getJson() {
		final JsonObject jsonObject = Utilities.getJsonObjectFromData(this);
		if (data != null) {
			jsonObject.add("data", data);
		}
		return jsonObject;
	}

	public long getCurrentTime() {
		return currentTime;
	}

	public <T extends SerializedDataBase> T getData(Function<JsonReader, T> dataInstance) {
		return dataInstance.apply(new JsonReader(data));
	}

	public static Response create(JsonObject jsonObject) {
		final JsonReader jsonReader = new JsonReader(jsonObject);
		return new Response(jsonReader, jsonObject);
	}
}
