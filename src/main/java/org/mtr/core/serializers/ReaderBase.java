package org.mtr.core.serializers;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ReaderBase {

	public abstract void unpackBoolean(String key, BooleanConsumer ifExists);

	public abstract boolean getBoolean(String key, boolean defaultValue);

	public abstract void iterateBooleanArray(String key, BooleanConsumer ifExists);

	public abstract void unpackInt(String key, IntConsumer ifExists);

	public abstract int getInt(String key, int defaultValue);

	public abstract void iterateIntArray(String key, IntConsumer ifExists);

	public abstract void unpackLong(String key, LongConsumer ifExists);

	public abstract long getLong(String key, long defaultValue);

	public abstract void iterateLongArray(String key, LongConsumer ifExists);

	public abstract void unpackDouble(String key, DoubleConsumer ifExists);

	public abstract double getDouble(String key, double defaultValue);

	public abstract void iterateDoubleArray(String key, DoubleConsumer ifExists);

	public abstract void unpackString(String key, Consumer<String> ifExists);

	public abstract String getString(String key, String defaultValue);

	public abstract void iterateStringArray(String key, Consumer<String> ifExists);

	public abstract boolean iterateReaderArray(String key, Consumer<ReaderBase> ifExists);

	public abstract void iterateKeys(String key, BiConsumer<String, ReaderBase> ifExists);

	protected final <U> boolean unpackValue(U value, Consumer<U> consumer) {
		if (value == null) {
			return false;
		} else {
			consumer.accept(value);
			return true;
		}
	}

	protected final <T, U> T getValueOrDefault(U value, T defaultValue, Function<U, T> function) {
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return function.apply(value);
			} catch (Exception ignored) {
				return defaultValue;
			}
		}
	}
}
