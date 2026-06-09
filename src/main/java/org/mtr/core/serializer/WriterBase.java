package org.mtr.core.serializer;

import java.util.Collection;

/**
 * Format-agnostic writer base used to serialise simulator state to JSON or MessagePack.
 *
 * <p>Companion to {@link ReaderBase}: every {@code write*(key, value)} writes one named field at
 * the current map nesting level; {@link #writeArray(String)} and {@link #writeChild(String)}
 * descend into nested arrays / objects. Subclasses ({@link JsonWriter}, {@link MessagePackWriter})
 * decide whether the structure is buffered (for headered formats like MessagePack) or flushed
 * directly (for JSON).</p>
 */
public abstract class WriterBase {

	/**
	 * Write a boolean field under {@code key}.
	 */
	public abstract void writeBoolean(String key, boolean value);

	/**
	 * Write an int field under {@code key}.
	 */
	public abstract void writeInt(String key, int value);

	/**
	 * Write a long field under {@code key}.
	 */
	public abstract void writeLong(String key, long value);

	/**
	 * Write a double field under {@code key}.
	 */
	public abstract void writeDouble(String key, double value);

	/**
	 * Write a string field under {@code key}.
	 */
	public abstract void writeString(String key, String value);

	/**
	 * Open an array field under {@code key}. The returned {@link Array} accepts values until it
	 * is discarded (no explicit close — the underlying writer flushes on serialisation).
	 */
	public abstract Array writeArray(String key);

	/**
	 * Open a nested-object field under {@code key}; returns the writer that scopes it.
	 */
	public abstract WriterBase writeChild(String key);

	/**
	 * Write {@code dataSet} as an array of nested objects, calling {@link SerializedDataBase#serializeData(WriterBase)}
	 * on each element.
	 */
	public final void writeDataset(Collection<? extends SerializedDataBase> dataSet, String key) {
		final WriterBase.Array writerBaseArray = writeArray(key);
		dataSet.forEach(data -> data.serializeData(writerBaseArray.writeChild()));
	}

	/**
	 * Cursor over a single array opened via {@link #writeArray(String)}. The element-write methods
	 * mirror the parent {@link WriterBase} but take no key (array elements are positional).
	 */
	public abstract static class Array {

		/**
		 * Append a boolean element.
		 */
		public abstract void writeBoolean(boolean value);

		/**
		 * Append an int element.
		 */
		public abstract void writeInt(int value);

		/**
		 * Append a long element.
		 */
		public abstract void writeLong(long value);

		/**
		 * Append a double element.
		 */
		public abstract void writeDouble(double value);

		/**
		 * Append a string element.
		 */
		public abstract void writeString(String value);

		/**
		 * Open a nested-object element; returns the writer that scopes it.
		 */
		public abstract WriterBase writeChild();
	}
}
