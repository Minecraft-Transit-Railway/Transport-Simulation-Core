package org.mtr.core.data;

import org.mtr.core.generated.data.LandmarkDensitySchema;
import org.mtr.core.generated.data.PassengerSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Passenger extends PassengerSchema {

	public Passenger() {
		super();
	}

	public Passenger(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
