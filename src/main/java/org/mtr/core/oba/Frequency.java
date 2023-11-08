package org.mtr.core.oba;

import org.mtr.core.data.Depot;
import org.mtr.core.generated.oba.FrequencySchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

public final class Frequency extends FrequencySchema implements Utilities {

	public Frequency(long currentMillis) {
		super(0, currentMillis + MILLIS_PER_DAY, Depot.CONTINUOUS_MOVEMENT_FREQUENCY / MILLIS_PER_SECOND);
	}

	public Frequency(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
