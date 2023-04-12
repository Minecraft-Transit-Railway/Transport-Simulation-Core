package org.mtr.core.tools;

import org.mtr.core.Main;
import org.mtr.core.data.ConditionalList;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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

	static double kilometersPerHourToMetersPerMillisecond(double speedKilometersPerHour) {
		return speedKilometersPerHour / 3600;
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

	static void awaitTermination(ExecutorService executorService) {
		try {
			while (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
				Main.LOGGER.warning("Termination failed, retrying...");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
