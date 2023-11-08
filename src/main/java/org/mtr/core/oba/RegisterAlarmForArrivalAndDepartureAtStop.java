package org.mtr.core.oba;

import org.mtr.core.generated.oba.RegisterAlarmForArrivalAndDepartureAtStopSchema;
import org.mtr.core.serializer.ReaderBase;

public final class RegisterAlarmForArrivalAndDepartureAtStop extends RegisterAlarmForArrivalAndDepartureAtStopSchema {

	public RegisterAlarmForArrivalAndDepartureAtStop(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
