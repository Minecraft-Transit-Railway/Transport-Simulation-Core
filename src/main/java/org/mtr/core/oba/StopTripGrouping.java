package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopTripGroupingSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopTripGrouping extends StopTripGroupingSchema {

	public StopTripGrouping(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
