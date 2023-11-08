package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopRouteScheduleSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopRouteSchedule extends StopRouteScheduleSchema {

	public StopRouteSchedule(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
