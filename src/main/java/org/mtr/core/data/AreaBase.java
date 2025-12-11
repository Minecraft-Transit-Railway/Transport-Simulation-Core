package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.legacy.data.DataFixer;

public abstract class AreaBase<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> extends SimpleAreaBase {

	public final ObjectArraySet<U> savedRails = new ObjectArraySet<>();

	public AreaBase(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	public AreaBase(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		DataFixer.unpackAreaBasePositions(readerBase, (value1, value2) -> {
			position1 = value1;
			position2 = value2;
		});
	}
}
