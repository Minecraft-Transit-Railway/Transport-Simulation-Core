package org.mtr.core.operation;

import org.mtr.core.data.Position;
import org.mtr.core.generated.operation.DirectionsRequestSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DirectionsRequest extends DirectionsRequestSchema {

	public DirectionsRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Position getStartPosition() {
		return startPosition;
	}

	public Position getEndPosition() {
		return endPosition;
	}

	public long getStartTime() {
		return startTime;
	}
}
