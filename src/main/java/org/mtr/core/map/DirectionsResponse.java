package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.map.DirectionsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DirectionsResponse extends DirectionsResponseSchema {

	public DirectionsResponse() {
		super();
	}

	public DirectionsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public ObjectArrayList<DirectionsConnection> getDirectionsConnections() {
		return connections;
	}
}
