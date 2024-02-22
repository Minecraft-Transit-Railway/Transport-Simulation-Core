package org.mtr.core.operation;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.generated.operation.DirectionsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

import javax.annotation.Nullable;

public final class DirectionsResponse extends DirectionsResponseSchema {

	public DirectionsResponse(long startMillis) {
		super(System.currentTimeMillis() - startMillis);
	}

	public DirectionsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void addSegment(@Nullable Platform startPlatform, @Nullable Platform endPlatform, @Nullable Route route, long duration, long waitingTime) {
		final Station startStation = startPlatform == null ? null : startPlatform.area;
		final Station endStation = endPlatform == null ? null : endPlatform.area;
		directionsSegments.add(new DirectionsSegment(
				startPlatform == null ? 0 : startPlatform.getId(),
				startPlatform == null ? "" : startPlatform.getName(),
				startStation == null ? "" : startStation.getName(),
				endPlatform == null ? 0 : endPlatform.getId(),
				endPlatform == null ? "" : endPlatform.getName(),
				endStation == null ? "" : endStation.getName(),
				route == null ? 0 : route.getId(),
				route == null ? 0 : route.getColor(),
				route == null ? "" : route.getName(),
				route == null ? "" : route.getRouteNumber(),
				route == null ? Route.CircularState.NONE : route.getCircularState(),
				duration,
				waitingTime
		));
	}
}
