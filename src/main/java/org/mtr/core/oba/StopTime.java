package org.mtr.core.oba;

import org.mtr.core.data.Trip;
import org.mtr.core.generated.oba.StopTimeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

public final class StopTime extends StopTimeSchema implements Utilities {

	public StopTime(Trip.StopTime stopTime, long offsetMillis) {
		super(
				Utilities.numberToPaddedHexString(stopTime.platformId),
				(stopTime.startTime + offsetMillis) / MILLIS_PER_SECOND,
				(stopTime.endTime + offsetMillis) / MILLIS_PER_SECOND,
				0,
				0,
				stopTime.customDestination
		);
	}

	public StopTime(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
