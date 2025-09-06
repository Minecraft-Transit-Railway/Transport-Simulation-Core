package org.mtr.core.operation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.operation.DirectionsGroupResponseSchema;

public final class DirectionsGroupResponse extends DirectionsGroupResponseSchema {

	public DirectionsGroupResponse(long refreshGraphTime, long refreshArrivalsTime, long pathFindingTime, ObjectArrayList<DirectionsResponse> directionsResponses) {
		super(refreshGraphTime, refreshArrivalsTime, pathFindingTime);
		this.directionsResponses.addAll(directionsResponses);
	}
}
