package org.mtr.core.operation;

import org.mtr.core.generated.operation.DirectionsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DirectionsResponse extends DirectionsResponseSchema {

	public DirectionsResponse() {
		super();
	}

	public DirectionsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void add(int index, Connection connection) {
		connections.add(index, connection);
	}
}
