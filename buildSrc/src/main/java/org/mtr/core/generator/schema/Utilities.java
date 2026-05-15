package org.mtr.core.generator.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Miscellaneous stateless helper methods for working with the JSON schema files
 * and for manipulating identifier strings.
 */
public interface Utilities {

	/**
	 * Converts a JSON schema {@code $ref} or file name to a capitalised Java/TypeScript
	 * class name.  If the input ends with {@code .json} (i.e. it is an object reference),
	 * the extension is stripped and the first letter is upper-cased; otherwise the raw
	 * string is returned unchanged.
	 *
	 * @param text the raw {@code $ref} value or file name
	 * @return the formatted class name
	 */
	static String formatRefName(String text) {
		return isObject(text) ? capitalizeFirstLetter(text.split("\\.json")[0]) : text;
	}

	/**
	 * Same as {@link #formatRefName} except the first letter is <em>not</em> capitalised,
	 * returning the raw base name without the {@code .json} extension.
	 *
	 * @param text the raw {@code $ref} value or file name
	 * @return the base name with no extension and no capitalisation change
	 */
	static String formatRefNameRaw(String text) {
		return isObject(text) ? text.split("\\.json")[0] : text;
	}

	/**
	 * Returns {@code true} if {@code text} ends with {@code .json}, indicating that it
	 * refers to a schema object (rather than a primitive or enum).
	 *
	 * @param text the string to test
	 * @return {@code true} if it is an object reference
	 */
	static boolean isObject(String text) {
		return text.endsWith(".json");
	}

	/**
	 * Upper-cases the first character of {@code text} using the English locale.
	 *
	 * @param text the input string
	 * @return the same string with its first letter in upper case, or an empty string if the input is empty
	 */
	static String capitalizeFirstLetter(String text) {
		return text.isEmpty() ? "" : text.substring(0, 1).toUpperCase(Locale.ENGLISH) + text.substring(1);
	}

	/**
	 * Returns {@code true} if the JSON array contains a string element equal to {@code data}.
	 *
	 * @param jsonArray the array to search, or {@code null} (treated as empty)
	 * @param data      the string to look for
	 * @return {@code true} if a matching primitive string element is found
	 */
	static boolean arrayContains(@Nullable JsonArray jsonArray, String data) {
		if (jsonArray != null) {
			for (final JsonElement jsonElement : jsonArray) {
				if (jsonElement.isJsonPrimitive() && jsonElement.getAsString().equals(data)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns the string value of a JSON primitive element, or {@code null} if the element
	 * is {@code null}.
	 *
	 * @param jsonElement the element to inspect, or {@code null}
	 * @return the string value, or {@code null}
	 */
	@Nullable
	static String getStringOrNull(@Nullable JsonElement jsonElement) {
		return jsonElement == null ? null : jsonElement.getAsString();
	}

	/**
	 * Iterates over every element of a JSON string array, invoking {@code consumer} for each
	 * element's string value.  A {@code null} array is silently ignored.
	 *
	 * @param jsonArray the JSON array to iterate, or {@code null}
	 * @param consumer  the action to perform for each string element
	 */
	static void iterateStringArray(@Nullable JsonArray jsonArray, Consumer<String> consumer) {
		if (jsonArray != null) {
			jsonArray.forEach(jsonElement -> consumer.accept(jsonElement.getAsString()));
		}
	}

	/**
	 * Iterates over every key-value pair in a JSON object, invoking {@code consumer} with the
	 * key string and the value as a {@link JsonObject}.  A {@code null} object is silently ignored.
	 *
	 * @param jsonObject the JSON object to iterate, or {@code null}
	 * @param consumer   the action to perform for each key/value pair
	 */
	static void iterateObject(@Nullable JsonObject jsonObject, BiConsumer<String, JsonObject> consumer) {
		if (jsonObject != null) {
			jsonObject.keySet().forEach(key -> consumer.accept(key, jsonObject.getAsJsonObject(key)));
		}
	}
}
