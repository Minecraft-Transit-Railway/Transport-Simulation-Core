package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopGroupingSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopGrouping extends StopGroupingSchema {

	public StopGrouping(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
