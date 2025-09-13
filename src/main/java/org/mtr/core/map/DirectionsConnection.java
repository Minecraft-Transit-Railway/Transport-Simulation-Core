package org.mtr.core.map;

import org.mtr.core.generated.map.DirectionsConnectionSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DirectionsConnection extends DirectionsConnectionSchema {

	public DirectionsConnection(String routeId, String startStationId, String endStationId, String startPlatformName, String endPlatformName, long startTime, long endTime, long walkingDistance) {
		super(routeId, startStationId, endStationId, startPlatformName, endPlatformName, startTime, endTime, walkingDistance);
	}

	public DirectionsConnection(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
