package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopRouteDirectionScheduleSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopRouteDirectionSchedule extends StopRouteDirectionScheduleSchema {

	public StopRouteDirectionSchedule(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
