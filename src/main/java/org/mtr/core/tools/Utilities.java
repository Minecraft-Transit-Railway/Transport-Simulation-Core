package org.mtr.core.tools;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.core.data.Rail;

import java.util.List;

public interface Utilities {

	int HOURS_PER_DAY = 24;
	int MILLIS_PER_HOUR = 60 * 60 * 1000;
	int MILLIS_PER_DAY = HOURS_PER_DAY * MILLIS_PER_HOUR;

	static boolean isBetween(double value, double value1, double value2) {
		return isBetween(value, value1, value2, 0);
	}

	static boolean isBetween(double value, double value1, double value2, double padding) {
		return value >= Math.min(value1, value2) - padding && value <= Math.max(value1, value2) + padding;
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

	static float round(double value, int decimalPlaces) {
		int factor = 1;
		for (int i = 0; i < decimalPlaces; i++) {
			factor *= 10;
		}
		return (float) Math.round(value * factor) / factor;
	}

	static <T, U extends List<T>> T getElement(U collection, int index) {
		return getElement(collection, index, null);
	}

	static <T, U extends List<T>> T getElement(U collection, int index, T defaultValue) {
		final T result;
		if (collection == null || index >= collection.size() || index < -collection.size()) {
			result = null;
		} else {
			result = collection.get((index < 0 ? collection.size() : 0) + index);
		}
		return result == null ? defaultValue : result;
	}

	static boolean containsRail(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, Position pos1, Position pos2) {
		return rails.containsKey(pos1) && rails.get(pos1).containsKey(pos2);
	}

	static long circularDifference(long value1, long value2, long totalDegrees) {
		long tempValue2 = value2;
		while (tempValue2 < 0) {
			tempValue2 += totalDegrees;
		}

		long tempValue1 = value1;
		while (tempValue1 < tempValue2) {
			tempValue1 += totalDegrees;
		}

		return (tempValue1 - tempValue2) % totalDegrees;
	}
}
