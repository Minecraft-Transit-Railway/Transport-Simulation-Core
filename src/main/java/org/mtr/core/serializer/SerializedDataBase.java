package org.mtr.core.serializer;

/**
 * The contract every persistable / wire-serialisable type implements. Schema-generated
 * classes implement this directly; hand-written subclasses inherit it through their
 * generated {@code *Schema} parent.
 *
 * <p>The pair {@link #updateData(ReaderBase)} / {@link #serializeData(WriterBase)} is the
 * symmetric read/write half &mdash; one populates this object from a stream, the other
 * writes it back. {@link #serializeFullData(WriterBase)} is the same as
 * {@link #serializeData(WriterBase)} unless a subclass overrides it to include extra
 * fields that should only be written when the object is sent over the wire (and not when
 * persisted).</p>
 */
public interface SerializedDataBase {

	/**
	 * Read fields from {@code readerBase} into this object. Implementations are expected to
	 * be idempotent &mdash; calling this twice with the same reader data yields the same
	 * resulting state.
	 *
	 * @param readerBase the source of the serialised representation
	 */
	void updateData(ReaderBase readerBase);

	/**
	 * Write this object's fields to {@code writerBase} in the canonical serialised form.
	 * Used both for persistence and for the wire format unless
	 * {@link #serializeFullData(WriterBase)} is overridden.
	 *
	 * @param writerBase the sink for the serialised representation
	 */
	void serializeData(WriterBase writerBase);

	/**
	 * Write the full wire form of this object &mdash; the on-disk fields plus any
	 * computed-on-send fields. Defaults to {@link #serializeData(WriterBase)}; override
	 * when an object exposes more state to clients than it persists.
	 *
	 * @param writerBase the sink for the serialised representation
	 */
	default void serializeFullData(WriterBase writerBase) {
		serializeData(writerBase);
	}
}
