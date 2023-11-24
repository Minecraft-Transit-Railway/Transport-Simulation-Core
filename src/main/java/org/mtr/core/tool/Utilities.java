package org.mtr.core.tool;

import org.mtr.core.Main;
import org.mtr.core.data.Position;
import org.mtr.core.serializer.JsonWriter;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.libraries.com.google.gson.GsonBuilder;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

public interface Utilities {

	int HOURS_PER_DAY = 24;
	int MILLIS_PER_SECOND = 1000;
	int MILLIS_PER_HOUR = 60 * 60 * MILLIS_PER_SECOND;
	int MILLIS_PER_DAY = HOURS_PER_DAY * MILLIS_PER_HOUR;

	static boolean isBetween(double value, double value1, double value2) {
		return isBetween(value, value1, value2, 0);
	}

	static boolean isBetween(double value, double value1, double value2, double padding) {
		return value >= Math.min(value1, value2) - padding && value <= Math.max(value1, value2) + padding;
	}

	static boolean isBetween(Position position, Position position1, Position position2, double padding) {
		return isBetween(position, position1.getX(), position1.getY(), position1.getZ(), position2.getX(), position2.getY(), position2.getZ(), padding);
	}

	static boolean isBetween(Position position, Vector position1, Vector position2, double padding) {
		return isBetween(position, position1.x, position1.y, position1.z, position2.x, position2.y, position2.z, padding);
	}

	static boolean isBetween(Position position, double x1, double y1, double z1, double x2, double y2, double z2, double padding) {
		return Utilities.isBetween(position.getX(), x1, x2, padding) &&
				Utilities.isBetween(position.getY(), y1, y2, padding) &&
				Utilities.isBetween(position.getZ(), z1, z2, padding);
	}

	static boolean isIntersecting(double value1, double value2, double value3, double value4) {
		return isBetween(value3, value1, value2) || isBetween(value4, value1, value2) || isBetween(value1, value3, value4) || isBetween(value2, value3, value4);
	}

	static int clamp(int value, int min, int max) {
		return Math.min(max, Math.max(min, value));
	}

	static long clamp(long value, long min, long max) {
		return Math.min(max, Math.max(min, value));
	}

	static float clamp(float value, float min, float max) {
		return Math.min(max, Math.max(min, value));
	}

	static double clamp(double value, double min, double max) {
		return Math.min(max, Math.max(min, value));
	}

	static double round(double value, int decimalPlaces) {
		int factor = 1;
		for (int i = 0; i < decimalPlaces; i++) {
			factor *= 10;
		}
		return (double) Math.round(value * factor) / factor;
	}

	static double getAverage(double a, double b) {
		return (a + b) / 2;
	}

	static String numberToPaddedHexString(long value) {
		return numberToPaddedHexString(value, Long.SIZE / 4);
	}

	static String numberToPaddedHexString(long value, int length) {
		return String.format("%" + length + "s", Long.toHexString(value)).replace(' ', '0').toUpperCase(Locale.ENGLISH);
	}

	static String formatName(String text) {
		return text.split("\\|\\|")[0].replace("|", " ");
	}

	static JsonObject parseJson(String data) {
		try {
			return JsonParser.parseString(data).getAsJsonObject();
		} catch (Exception ignored) {
			return new JsonObject();
		}
	}

	static String prettyPrint(String string) {
		return prettyPrint(parseJson(string));
	}

	static String prettyPrint(JsonElement jsonElement) {
		return new GsonBuilder().setPrettyPrinting().create().toJson(jsonElement);
	}

	static double kilometersPerHourToMetersPerMillisecond(double speedKilometersPerHour) {
		return speedKilometersPerHour / 3600;
	}

	static <T, U extends List<T>> T getElement(U collection, int index) {
		return getElement(collection, index, null);
	}

	static <T, U extends List<T>> T getElement(@Nullable U collection, int index, @Nullable T defaultValue) {
		final T result;
		if (collection == null || index >= collection.size() || index < -collection.size()) {
			result = null;
		} else {
			result = collection.get((index < 0 ? collection.size() : 0) + index);
		}
		return result == null ? defaultValue : result;
	}

	static <T extends ConditionalList> int getIndexFromConditionalList(List<T> list, double value) {
		if (list.isEmpty()) {
			return -1;
		} else {
			final int listSize = list.size();
			int index = listSize / 2;
			int lowIndex = -1;
			int highIndex = listSize;

			while (true) {
				if (list.get(index).matchesCondition(value)) {
					lowIndex = index;
				} else {
					highIndex = index;
				}

				if (lowIndex + 1 == highIndex) {
					return lowIndex < 0 ? -1 : lowIndex;
				}

				index = Utilities.clamp((lowIndex + highIndex) / 2, 0, listSize - 1);
			}
		}
	}

	static <T extends SerializedDataBase> JsonObject getJsonObjectFromData(T data) {
		final JsonObject jsonObject = new JsonObject();
		data.serializeData(new JsonWriter(jsonObject));
		return jsonObject;
	}

	static long circularDifference(long value1, long value2, long totalDegrees) {
		long tempValue1 = value1;
		final long halfTotalDegrees = totalDegrees / 2;

		if (tempValue1 - halfTotalDegrees > value2 || tempValue1 + halfTotalDegrees <= value2) {
			tempValue1 -= (tempValue1 - halfTotalDegrees - value2) / totalDegrees * totalDegrees;
		}

		while (tempValue1 - halfTotalDegrees > value2) {
			tempValue1 -= totalDegrees;
		}

		while (tempValue1 + halfTotalDegrees <= value2) {
			tempValue1 += totalDegrees;
		}

		return tempValue1 - value2;
	}

	static int compare(long value1, long value2, IntSupplier ifZero) {
		final int result = Long.compare(value1, value2);
		return result == 0 ? ifZero.getAsInt() : result;
	}

	static int compare(String value1, String value2, IntSupplier ifZero) {
		try {
			return compare(Long.parseLong(value1), Long.parseLong(value2), ifZero);
		} catch (Exception ignored) {
			final int result = value1.compareTo(value2);
			return result == 0 ? ifZero.getAsInt() : result;
		}
	}

	static void awaitTermination(ExecutorService executorService) {
		try {
			while (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
				Main.LOGGER.warning("Termination failed, retrying...");
			}
		} catch (Exception e) {
			Main.logException(e);
		}
	}
}
