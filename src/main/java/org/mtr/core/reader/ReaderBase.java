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

	public T get(String key) {
		return map.get(key);
	}

	public void forEach(BiConsumer<String, T> consumer) {
		map.forEach(consumer);
	}

	public void unpackBoolean(String key, Consumer<Boolean> ifExists) {
		unpack(key, this::getBoolean, ifExists);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return getOrDefault(key, defaultValue, this::getBoolean);
	}

	public boolean iterateBooleanArray(String key, Consumer<Boolean> ifExists) {
		return iterateArray(key, this::getBoolean, ifExists);
	}

	public void unpackInt(String key, Consumer<Integer> ifExists) {
		unpack(key, this::getInt, ifExists);
	}

	public int getInt(String key, int defaultValue) {
		return getOrDefault(key, defaultValue, this::getInt);
	}

	public void iterateIntArray(String key, Consumer<Integer> ifExists) {
		iterateArray(key, this::getInt, ifExists);
	}

	public void unpackLong(String key, Consumer<Long> ifExists) {
		unpack(key, this::getLong, ifExists);
	}

	public long getLong(String key, long defaultValue) {
		return getOrDefault(key, defaultValue, this::getLong);
	}

	public void iterateLongArray(String key, Consumer<Long> ifExists) {
		iterateArray(key, this::getLong, ifExists);
	}

	public void unpackFloat(String key, Consumer<Float> ifExists) {
		unpack(key, this::getFloat, ifExists);
	}

	public float getFloat(String key, float defaultValue) {
		return getOrDefault(key, defaultValue, this::getFloat);
	}

	public void iterateFloatArray(String key, Consumer<Float> ifExists) {
		iterateArray(key, this::getFloat, ifExists);
	}

	public void unpackDouble(String key, Consumer<Double> ifExists) {
		unpack(key, this::getDouble, ifExists);
	}

	public double getDouble(String key, double defaultValue) {
		return getOrDefault(key, defaultValue, this::getDouble);
	}

	public void iterateDoubleArray(String key, Consumer<Double> ifExists) {
		iterateArray(key, this::getDouble, ifExists);
	}

	public void unpackString(String key, Consumer<String> ifExists) {
		unpack(key, this::getString, ifExists);
	}

	public String getString(String key, String defaultValue) {
		return getOrDefault(key, defaultValue, this::getString);
	}

	public void iterateStringArray(String key, Consumer<String> ifExists) {
		iterateArray(key, this::getString, ifExists);
	}

	public abstract boolean iterateReaderArray(String key, Consumer<U> ifExists);

	public abstract void iterateReaderMap(String key, Consumer<U> ifExists);

	protected abstract boolean getBoolean(T value);

	protected abstract int getInt(T value);

	protected abstract long getLong(T value);

	protected abstract float getFloat(T value);

	protected abstract double getDouble(T value);

	protected abstract String getString(T value);

	protected abstract void iterateArray(T value, Consumer<T> consumer);

	protected abstract void iterateMap(T value, BiConsumer<String, T> consumer);

	protected <V> boolean iterateArray(String key, Function<T, V> function, Consumer<V> ifExists) {
		return unpack(key, value -> value, value -> iterateArray(value, arrayValue -> ifExists.accept(function.apply(arrayValue))));
	}

	protected void iterateMap(String key, Function<Map<String, T>, U> function, Consumer<U> ifExists) {
		final Map<String, T> newMap = new HashMap<>();
		unpack(key, value -> value, value -> iterateMap(value, newMap::put));
		ifExists.accept(function.apply(newMap));
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
