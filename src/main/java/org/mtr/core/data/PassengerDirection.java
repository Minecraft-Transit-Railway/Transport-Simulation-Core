package org.mtr.core.data;

import org.mtr.core.generated.data.PassengerDirectionSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.ConditionalList;

public final class PassengerDirection extends PassengerDirectionSchema implements ConditionalList {

	public PassengerDirection(long routeId, long startPlatformId, long endPlatformId, long startTime, long endTime) {
		super(routeId, startPlatformId, endPlatformId, startTime, endTime);
	}

	public PassengerDirection(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public boolean matchesCondition(double value) {
		return value >= startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public long getRouteId() {
		return routeId;
	}

	public long getStartPlatformId() {
		return startPlatformId;
	}

	public long getEndPlatformId() {
		return endPlatformId;
	}
}
