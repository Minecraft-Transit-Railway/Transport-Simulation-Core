package org.mtr.core.reader;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ReaderBase<T, U extends ReaderBase<T, U>> {

	private final Object2ObjectArrayMap<String, T> map;

	public ReaderBase(Object2ObjectArrayMap<String, T> map) {
		this.map = map;
	}

	protected ReaderBase(T value) {
		map = new Object2ObjectArrayMap<>();
		iterateMap(value, map::put);
	}

	public final T get(String key) {
		return map.get(key);
	}

	public final void forEach(BiConsumer<String, T> consumer) {
		map.forEach(consumer);
	}

	public final void unpackBoolean(String key, BooleanConsumer ifExists) {
		unpack(key, this::getBoolean, ifExists);
	}

	public final boolean getBoolean(String key, boolean defaultValue) {
		return getOrDefault(key, defaultValue, this::getBoolean);
	}

	public final boolean iterateBooleanArray(String key, BooleanConsumer ifExists) {
		return iterateArray(key, this::getBoolean, ifExists);
	}

	public final void unpackInt(String key, IntConsumer ifExists) {
		unpack(key, this::getInt, ifExists);
	}

	public final int getInt(String key, int defaultValue) {
		return getOrDefault(key, defaultValue, this::getInt);
	}

	public final void iterateIntArray(String key, IntConsumer ifExists) {
		iterateArray(key, this::getInt, ifExists);
	}

	public final void unpackLong(String key, LongConsumer ifExists) {
		unpack(key, this::getLong, ifExists);
	}

	public final long getLong(String key, long defaultValue) {
		return getOrDefault(key, defaultValue, this::getLong);
	}

	public final void iterateLongArray(String key, LongConsumer ifExists) {
		iterateArray(key, this::getLong, ifExists);
	}

	public final void unpackDouble(String key, DoubleConsumer ifExists) {
		unpack(key, this::getDouble, ifExists);
	}

	public final double getDouble(String key, double defaultValue) {
		return getOrDefault(key, defaultValue, this::getDouble);
	}

	public final void iterateDoubleArray(String key, DoubleConsumer ifExists) {
		iterateArray(key, this::getDouble, ifExists);
	}

	public final void unpackString(String key, Consumer<String> ifExists) {
		unpack(key, this::getString, ifExists);
	}

	public final String getString(String key, String defaultValue) {
		return getOrDefault(key, defaultValue, this::getString);
	}

	public final void iterateStringArray(String key, Consumer<String> ifExists) {
		iterateArray(key, this::getString, ifExists);
	}

	public abstract boolean iterateReaderArray(String key, Consumer<U> ifExists);

	public abstract U getChild(String key);

	protected abstract boolean getBoolean(T value);

	protected abstract int getInt(T value);

	protected abstract long getLong(T value);

	protected abstract double getDouble(T value);

	protected abstract String getString(T value);

	protected abstract void iterateArray(T value, Consumer<T> consumer);

	protected abstract void iterateMap(T value, BiConsumer<String, T> consumer);

	protected final <V> boolean iterateArray(String key, Function<T, V> function, Consumer<V> ifExists) {
		return unpack(key, value -> value, value -> iterateArray(value, arrayValue -> ifExists.accept(function.apply(arrayValue))));
	}

	protected final U getChild(String key, Function<Object2ObjectArrayMap<String, T>, U> function) {
		final Object2ObjectArrayMap<String, T> newMap = new Object2ObjectArrayMap<>();
		unpack(key, value -> value, value -> iterateMap(value, newMap::put));
		return function.apply(newMap);
	}

	private <V> V getOrDefault(String key, V defaultValue, Function<T, V> function) {
		try {
			final V value = function.apply(map.get(key));
			if (value == null) {
				return defaultValue;
			} else {
				return value;
			}
		} catch (Exception ignored) {
			return defaultValue;
		}
	}

	private <V> boolean unpack(String key, Function<T, V> function, Consumer<V> ifExists) {
		final V value = getOrDefault(key, null, function);
		if (value == null) {
			return false;
		} else {
			ifExists.accept(value);
			return true;
		}
	}
}
