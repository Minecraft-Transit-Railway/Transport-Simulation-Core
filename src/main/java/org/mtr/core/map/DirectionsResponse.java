package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.map.DirectionsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DirectionsResponse extends DirectionsResponseSchema {

	public DirectionsResponse(
			long totalRefreshGraphTime,
			long totalRefreshArrivalsTime,
			long totalPathFindingTime,
			long longestRefreshGraphTime,
			long longestRefreshArrivalsTime,
			long longestPathFindingTime
	) {
		super(
				totalRefreshGraphTime,
				totalRefreshArrivalsTime,
				totalPathFindingTime,
				longestRefreshGraphTime,
				longestRefreshArrivalsTime,
				longestPathFindingTime
		);
	}

	public DirectionsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public ObjectArrayList<DirectionsConnection> getDirectionsConnections() {
		return connections;
	}
}
