package org.mtr.core.reader;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ReaderBase<T, U extends ReaderBase<T, U>> {

	private final Map<String, T> map;

	public ReaderBase(Map<String, T> map) {
		this.map = map;
	}

	protected ReaderBase(T value) {
		map = new HashMap<>();
		iterateMap(value, map::put);
	}

	public final T get(String key) {
		return map.get(key);
	}

	public final void forEach(BiConsumer<String, T> consumer) {
		map.forEach(consumer);
	}

	public final void unpackBoolean(String key, Consumer<Boolean> ifExists) {
		unpack(key, this::getBoolean, ifExists);
	}

	public final boolean getBoolean(String key, boolean defaultValue) {
		return getOrDefault(key, defaultValue, this::getBoolean);
	}

	public final boolean iterateBooleanArray(String key, Consumer<Boolean> ifExists) {
		return iterateArray(key, this::getBoolean, ifExists);
	}

	public final void unpackInt(String key, Consumer<Integer> ifExists) {
		unpack(key, this::getInt, ifExists);
	}

	public final int getInt(String key, int defaultValue) {
		return getOrDefault(key, defaultValue, this::getInt);
	}

	public final void iterateIntArray(String key, Consumer<Integer> ifExists) {
		iterateArray(key, this::getInt, ifExists);
	}

	public final void unpackLong(String key, Consumer<Long> ifExists) {
		unpack(key, this::getLong, ifExists);
	}

	public final long getLong(String key, long defaultValue) {
		return getOrDefault(key, defaultValue, this::getLong);
	}

	public final void iterateLongArray(String key, Consumer<Long> ifExists) {
		iterateArray(key, this::getLong, ifExists);
	}

	public final void unpackDouble(String key, Consumer<Double> ifExists) {
		unpack(key, this::getDouble, ifExists);
	}

	public final double getDouble(String key, double defaultValue) {
		return getOrDefault(key, defaultValue, this::getDouble);
	}

	public final void iterateDoubleArray(String key, Consumer<Double> ifExists) {
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

	protected final U getChild(String key, Function<Map<String, T>, U> function) {
		final Map<String, T> newMap = new HashMap<>();
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
