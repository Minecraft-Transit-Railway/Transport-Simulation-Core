package org.mtr.core.operation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.operation.DirectionsGroupRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public final class DirectionsGroupRequest extends DirectionsGroupRequestSchema {

	public DirectionsGroupRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public DirectionsGroupResponse getDirections(Simulator simulator) {
		final long millis1 = System.currentTimeMillis();
		simulator.directionsFinder.refreshGraph((int) maxWalkingDistance);
		final long millis2 = System.currentTimeMillis();
		simulator.directionsFinder.refreshArrivals();
		final long millis3 = System.currentTimeMillis();
		final ObjectArrayList<DirectionsResponse> directionsResponses = simulator.directionsFinder.earliestArrivalCSA(directionsRequests);
		return new DirectionsGroupResponse(millis2 - millis1, millis3 - millis2, System.currentTimeMillis() - millis3, directionsResponses);
	}
}
