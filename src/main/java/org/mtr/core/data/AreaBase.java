package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.legacy.data.DataFixer;

/**
 * Common state for any rectangular area in the world that owns a set of
 * {@link SavedRailBase}s &mdash; today that's {@link Station} (whose owned rails are
 * {@link Platform}s) and {@link Depot} (whose owned rails are {@link Siding}s).
 *
 * <p>The two-corner rectangle ({@code position1} / {@code position2}, inherited from
 * {@link SimpleAreaBase}) defines the area's footprint; {@link #savedRails} caches every
 * rail whose track passes through it, populated lazily by the simulator.</p>
 *
 * @param <T> the concrete area subtype (curiously-recurring template parameter for
 *            self-referential generics)
 * @param <U> the concrete {@link SavedRailBase} subtype this area owns
 */
public abstract class AreaBase<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> extends SimpleAreaBase {

	/**
	 * Rails inside this area's bounding box, populated by the simulator on tick.
	 */
	public final ObjectArraySet<U> savedRails = new ObjectArraySet<>();

	/**
	 * Construct a fresh area.
	 *
	 * @param transportMode the area's transport mode (train, boat, &hellip;)
	 * @param data          the owning {@link Data} container (typically the {@code Simulator})
	 */
	public AreaBase(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	/**
	 * Reconstruct an area from its serialised form, applying any legacy data fixers needed
	 * for older save versions.
	 *
	 * @param readerBase the source of the serialised representation
	 * @param data       the owning {@link Data} container (typically the {@code Simulator})
	 */
	public AreaBase(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		DataFixer.unpackAreaBasePositions(readerBase, (value1, value2) -> {
			position1 = value1;
			position2 = value2;
		});
	}
}
