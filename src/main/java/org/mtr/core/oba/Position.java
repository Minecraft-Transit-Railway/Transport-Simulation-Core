package org.mtr.core.oba;

import org.mtr.core.generated.oba.PositionSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Position extends PositionSchema {

	public Position(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	Position(double lat, double lon) {
		super(lat, lon);
	}
}
