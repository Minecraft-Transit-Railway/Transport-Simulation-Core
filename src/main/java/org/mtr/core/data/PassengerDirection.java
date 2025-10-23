package org.mtr.core.data;

import org.mtr.core.generated.data.PassengerDirectionSchema;
import org.mtr.core.serializer.ReaderBase;

public final class PassengerDirection extends PassengerDirectionSchema {

	public PassengerDirection(long sidingId, long vehicleId, long startPlatformId, long endPlatformId, long startTime, long endTime) {
		super(sidingId, vehicleId, startPlatformId, endPlatformId, startTime, endTime);
	}

	public PassengerDirection(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
