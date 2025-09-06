package org.mtr.core.operation;

import org.mtr.core.generated.operation.ConnectionSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Connection extends ConnectionSchema {

	public Connection(long routeId, long startPlatformId, long endPlatformId, long startTime, long endTime, long walkingDistance) {
		super(routeId, startPlatformId, endPlatformId, startTime, endTime, walkingDistance);
	}

	public Connection(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
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

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public long getWalkingDistance() {
		return walkingDistance;
	}
}
