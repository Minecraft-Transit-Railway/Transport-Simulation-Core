package org.mtr.core.servlet;

import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public final class QueueObject {

	public final String key;
	public final SerializedDataBase data;
	@Nullable
	private final Consumer<JsonObject> callback;

	public QueueObject(String key, SerializedDataBase data, @Nullable Consumer<JsonObject> callback) {
		this.key = key;
		this.data = data;
		this.callback = callback;
	}

	public void runCallback(JsonObject jsonObject) {
		if (callback != null) {
			callback.accept(jsonObject);
		}
	}
}
