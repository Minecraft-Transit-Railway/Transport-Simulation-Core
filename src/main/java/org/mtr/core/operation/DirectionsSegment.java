package org.mtr.core.operation;

import org.mtr.core.data.Route;
import org.mtr.core.generated.operation.DirectionsSegmentSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DirectionsSegment extends DirectionsSegmentSchema {

	DirectionsSegment(long startPlatformId, String startPlatformName, String startStationName, long endPlatformId, String endPlatformName, String endStationName, long routeId, long routeColor, String routeName, String routeNumber, Route.CircularState routeCircularState, long duration, long waitingTime) {
		super(startPlatformId, startPlatformName, startStationName, endPlatformId, endPlatformName, endStationName, routeId, routeColor, routeName, routeNumber, routeCircularState, duration, waitingTime);
	}

	public DirectionsSegment(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
