package org.mtr.core.serializer;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Format-agnostic reader base used to deserialise persisted simulator state.
 *
 * <p>Concrete subclasses ({@link JsonReader}, {@link MessagePackReader}) wrap a JSON or
 * MessagePack tree and translate primitive / array / nested-object lookups into the same
 * stable abstract API the schema-generated classes consume. This keeps domain-class
 * deserialisation independent of the on-disk format.</p>
 *
 * <p>All "unpack" methods are absent-tolerant: missing keys simply skip the consumer instead of
 * throwing. Per-element decoding errors inside arrays are logged at {@code debug} level rather
 * than aborting the whole parse — see {@link #getValueOrDefault(Object, Object, Function)}.</p>
 */
@Log4j2
public abstract class ReaderBase {

	/**
	 * Invoke {@code ifExists} with the boolean stored under {@code key}, if any.
	 *
	 * @param key      key to look up in the underlying map
	 * @param ifExists callback fed the decoded boolean if {@code key} is present
	 */
	public abstract void unpackBoolean(String key, BooleanConsumer ifExists);

	/**
	 * @param key          key to look up
	 * @param defaultValue value returned if {@code key} is absent or undecodable
	 * @return the decoded boolean, or {@code defaultValue} if missing / malformed
	 */
	public abstract boolean getBoolean(String key, boolean defaultValue);

	/**
	 * Iterate a boolean array stored under {@code key}, clearing the destination first.
	 *
	 * @param key       key to look up
	 * @param clearList runnable that empties the destination list before refilling
	 * @param ifExists  callback fed each decoded element
	 */
	public abstract void iterateBooleanArray(String key, Runnable clearList, BooleanConsumer ifExists);

	/**
	 * @see #unpackBoolean(String, BooleanConsumer)
	 */
	public abstract void unpackInt(String key, IntConsumer ifExists);

	/**
	 * @see #getBoolean(String, boolean)
	 */
	public abstract int getInt(String key, int defaultValue);

	/**
	 * @see #iterateBooleanArray(String, Runnable, BooleanConsumer)
	 */
	public abstract void iterateIntArray(String key, Runnable clearList, IntConsumer ifExists);

	/**
	 * @see #unpackBoolean(String, BooleanConsumer)
	 */
	public abstract void unpackLong(String key, LongConsumer ifExists);

	/**
	 * @see #getBoolean(String, boolean)
	 */
	public abstract long getLong(String key, long defaultValue);

	/**
	 * @see #iterateBooleanArray(String, Runnable, BooleanConsumer)
	 */
	public abstract void iterateLongArray(String key, Runnable clearList, LongConsumer ifExists);

	/**
	 * @see #unpackBoolean(String, BooleanConsumer)
	 */
	public abstract void unpackDouble(String key, DoubleConsumer ifExists);

	/**
	 * @see #getBoolean(String, boolean)
	 */
	public abstract double getDouble(String key, double defaultValue);

	/**
	 * @see #iterateBooleanArray(String, Runnable, BooleanConsumer)
	 */
	public abstract void iterateDoubleArray(String key, Runnable clearList, DoubleConsumer ifExists);

	/**
	 * @see #unpackBoolean(String, BooleanConsumer)
	 */
	public abstract void unpackString(String key, Consumer<String> ifExists);

	/**
	 * @see #getBoolean(String, boolean)
	 */
	public abstract String getString(String key, String defaultValue);

	/**
	 * @see #iterateBooleanArray(String, Runnable, BooleanConsumer)
	 */
	public abstract void iterateStringArray(String key, Runnable clearList, Consumer<String> ifExists);

	/**
	 * Iterate an array of nested objects stored under {@code key}, exposing each as its own
	 * {@link ReaderBase}.
	 *
	 * @param key       key to look up
	 * @param clearList runnable that empties the destination list before refilling
	 * @param ifExists  callback fed a child reader for each element
	 */
	public abstract void iterateReaderArray(String key, Runnable clearList, Consumer<ReaderBase> ifExists);

	/**
	 * @param key key to look up
	 * @return a child reader over the nested object at {@code key}, or an empty reader if absent
	 */
	public abstract ReaderBase getChild(String key);

	/**
	 * Invoke {@code ifExists} with a child reader for the nested object at {@code key}, if any.
	 *
	 * @param key      key to look up
	 * @param ifExists callback fed the child reader if {@code key} is present
	 */
	public abstract void unpackChild(String key, Consumer<ReaderBase> ifExists);

	/**
	 * Merge another reader's keys into this one, overwriting any colliding entries.
	 * Implementations are no-ops when {@code readerBase} is of a different concrete type.
	 *
	 * @param readerBase reader whose keys should be folded into this one
	 */
	public abstract void merge(ReaderBase readerBase);

	/**
	 * Apply {@code consumer} to {@code value} when non-null; log any thrown exception at
	 * {@code error} level so we never silently lose a malformed payload.
	 */
	protected final <U> void unpackValue(@Nullable U value, Consumer<U> consumer) {
		if (value != null) {
			try {
				consumer.accept(value);
			} catch (Exception e) {
				log.error("Failed to unpack value {}", value, e);
			}
		}
	}

	/**
	 * Apply {@code function} to {@code value} when non-null, returning {@code defaultValue} on
	 * either a null input or a thrown exception. Decode failures are logged at {@code debug} level
	 * (per CODE_STYLES §3.14) — they are expected when on-disk data shifts type and the schema
	 * falls back to its default, but they should still be observable when chasing data drift.
	 */
	protected final <T, U> T getValueOrDefault(@Nullable U value, T defaultValue, Function<U, T> function) {
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return function.apply(value);
			} catch (Exception e) {
				log.debug("Decode of value {} failed; falling back to default", value, e);
				return defaultValue;
			}
		}
	}
}
