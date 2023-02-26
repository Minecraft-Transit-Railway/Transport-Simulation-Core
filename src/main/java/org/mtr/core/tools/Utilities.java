package org.mtr.core.tools;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.core.data.Rail;

import java.util.List;

public interface Utilities {

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
		if (collection == null || index >= collection.size() || index < -collection.size()) {
			return null;
		} else {
			return collection.get((index < 0 ? collection.size() : 0) + index);
		}
	}

	static boolean containsRail(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, Position pos1, Position pos2) {
		return rails.containsKey(pos1) && rails.get(pos1).containsKey(pos2);
	}
}
