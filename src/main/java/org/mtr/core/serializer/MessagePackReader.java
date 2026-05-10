package org.mtr.core.serializer;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import lombok.extern.log4j.Log4j2;
import org.msgpack.core.MessageTypeException;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.mtr.legacy.data.DataFixer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link ReaderBase} backed by an in-memory MessagePack {@link Value} tree.
 *
 * <p>This is the on-disk format the simulator persists to (see {@code FileLoader}). On read,
 * each top-level key is routed through {@link DataFixer#readerBaseConvertKey(String, Value, Object2ObjectArrayMap)}
 * so legacy on-disk schemas keep deserialising into the current model.</p>
 */
@Log4j2
public final class MessagePackReader extends ReaderBase {

	private final Object2ObjectArrayMap<String, Value> map;

	/**
	 * Construct an empty reader; every lookup returns its default.
	 */
	public MessagePackReader() {
		this.map = new Object2ObjectArrayMap<>();
	}

	/**
	 * Read a MessagePack map header from {@code messageUnpacker} and consume the matching number
	 * of {@code (key, value)} pairs. Re-throws {@link MessageTypeException} so the caller can
	 * distinguish "not a MessagePack map" from "malformed value inside the map" — the latter is
	 * logged and tolerated.
	 */
	public MessagePackReader(MessageUnpacker messageUnpacker) throws MessageTypeException {
		map = new Object2ObjectArrayMap<>();
		try {
			final int size = messageUnpacker.unpackMapHeader();
			for (int i = 0; i < size; i++) {
				DataFixer.readerBaseConvertKey(messageUnpacker.unpackString(), messageUnpacker.unpackValue(), map);
			}
		} catch (MessageTypeException e) {
			throw e;
		} catch (Exception e) {
			log.error("Failed to read MessagePack input", e);
		}
	}

	private MessagePackReader(Value value) {
		map = new Object2ObjectArrayMap<>();
		iterateMap(value, (mapKey, mapValue) -> DataFixer.readerBaseConvertKey(mapKey, mapValue, map));
	}

	@Override
	public void unpackBoolean(String key, BooleanConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getBoolean(value)));
	}

	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		return getOrDefault(key, defaultValue, MessagePackReader::getBoolean);
	}

	@Override
	public void iterateBooleanArray(String key, Runnable clearList, BooleanConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getBoolean(arrayValue))));
	}

	@Override
	public void unpackInt(String key, IntConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getInt(value)));
	}

	@Override
	public int getInt(String key, int defaultValue) {
		return getOrDefault(key, defaultValue, MessagePackReader::getInt);
	}

	@Override
	public void iterateIntArray(String key, Runnable clearList, IntConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getInt(arrayValue))));
	}

	@Override
	public void unpackLong(String key, LongConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getLong(value)));
	}

	@Override
	public long getLong(String key, long defaultValue) {
		return getOrDefault(key, defaultValue, MessagePackReader::getLong);
	}

	@Override
	public void iterateLongArray(String key, Runnable clearList, LongConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getLong(arrayValue))));
	}

	@Override
	public void unpackDouble(String key, DoubleConsumer ifExists) {
		unpack(key, value -> ifExists.accept(getDouble(value)));
	}

	@Override
	public double getDouble(String key, double defaultValue) {
		return getOrDefault(key, defaultValue, MessagePackReader::getDouble);
	}

	@Override
	public void iterateDoubleArray(String key, Runnable clearList, DoubleConsumer ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getDouble(arrayValue))));
	}

	@Override
	public void unpackString(String key, Consumer<String> ifExists) {
		unpack(key, value -> ifExists.accept(getString(value)));
	}

	@Override
	public String getString(String key, String defaultValue) {
		return getOrDefault(key, defaultValue, MessagePackReader::getString);
	}

	@Override
	public void iterateStringArray(String key, Runnable clearList, Consumer<String> ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(getString(arrayValue))));
	}

	@Override
	public void iterateReaderArray(String key, Runnable clearList, Consumer<ReaderBase> ifExists) {
		unpack(key, value -> iterateArray(value, clearList, arrayValue -> ifExists.accept(new MessagePackReader(arrayValue))));
	}

	@Override
	public ReaderBase getChild(String key) {
		return getOrDefault(key, new MessagePackReader(), MessagePackReader::new);
	}

	@Override
	public void unpackChild(String key, Consumer<ReaderBase> ifExists) {
		unpack(key, value -> ifExists.accept(new MessagePackReader(value)));
	}

	@Override
	public void merge(ReaderBase readerBase) {
		if (readerBase instanceof final MessagePackReader other) {
			map.putAll(other.map);
		}
	}

	/**
	 * Internal-only: iterate over the MessagePack map stored at {@code key} as raw
	 * {@code (String, Value)} pairs. Used by {@link DataFixer} when migrating legacy on-disk
	 * shapes that the regular schema-based reader cannot express. <strong>Do not call from user
	 * code.</strong>
	 */
	@Deprecated
	@SuppressWarnings("DeprecatedIsStillUsed")
	public void iterateMap(String key, BiConsumer<String, Value> consumer) {
		final Value value = map.get(key);
		if (value != null) {
			iterateMap(value, consumer);
		}
	}

	private void unpack(String key, Consumer<Value> consumer) {
		unpackValue(map.get(key), consumer);
	}

	private <T> T getOrDefault(String key, T defaultValue, Function<Value, T> function) {
		return getValueOrDefault(map.get(key), defaultValue, function);
	}

	private static boolean getBoolean(Value value) {
		return value.asBooleanValue().getBoolean();
	}

	private static int getInt(Value value) {
		return value.asIntegerValue().asInt();
	}

	private static long getLong(Value value) {
		return value.asIntegerValue().asLong();
	}

	private static double getDouble(Value value) {
		return value.asFloatValue().toDouble();
	}

	private static String getString(Value value) {
		return value.asStringValue().asString();
	}

	private static void iterateArray(Value value, Runnable clearList, Consumer<Value> consumer) {
		clearList.run();
		value.asArrayValue().forEach(arrayValue -> {
			try {
				consumer.accept(arrayValue);
			} catch (Exception e) {
				// One bad element should not abort the whole array — log and skip it (CODE_STYLES §3.14).
				log.debug("Skipping malformed MessagePack array element {}", arrayValue, e);
			}
		});
	}

	private static void iterateMap(Value value, BiConsumer<String, Value> consumer) {
		value.asMapValue().entrySet().forEach(entry -> {
			try {
				consumer.accept(getString(entry.getKey()), entry.getValue());
			} catch (Exception e) {
				// One bad entry should not abort the whole map — log and skip it (CODE_STYLES §3.14).
				log.debug("Skipping malformed MessagePack map entry {}", entry, e);
			}
		});
	}
}
