package org.mtr.core.generator.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface Utilities {

	static String formatRefName(String text) {
		return isObject(text) ? capitalizeFirstLetter(text.split("\\.json")[0]) : text;
	}

	static boolean isObject(String text) {
		return text.endsWith(".json");
	}

	static String capitalizeFirstLetter(String text) {
		return text.isEmpty() ? "" : text.substring(0, 1).toUpperCase(Locale.ENGLISH) + text.substring(1);
	}

	static boolean arrayContains(JsonArray jsonArray, String data) {
		if (jsonArray != null) {
			for (final JsonElement jsonElement : jsonArray) {
				if (jsonElement.isJsonPrimitive() && jsonElement.getAsString().equals(data)) {
					return true;
				}
			}
		}
		return false;
	}

	static String getStringOrNull(JsonElement jsonElement) {
		return jsonElement == null ? null : jsonElement.getAsString();
	}

	static void iterateStringArray(JsonArray jsonArray, Consumer<String> consumer) {
		if (jsonArray != null) {
			jsonArray.forEach(jsonElement -> consumer.accept(jsonElement.getAsString()));
		}
	}

	static void iterateObject(JsonObject jsonObject, BiConsumer<String, JsonObject> consumer) {
		if (jsonObject != null) {
			jsonObject.keySet().forEach(key -> consumer.accept(key, jsonObject.getAsJsonObject(key)));
		}
	}
}
