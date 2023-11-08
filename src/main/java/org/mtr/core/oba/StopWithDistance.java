package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopWithDistanceSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopWithDistance extends StopWithDistanceSchema {

	public StopWithDistance(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
