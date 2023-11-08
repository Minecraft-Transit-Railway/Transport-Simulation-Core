package org.mtr.core.oba;

import org.mtr.core.generated.oba.ScheduleForRouteSchema;
import org.mtr.core.serializer.ReaderBase;

public final class ScheduleForRoute extends ScheduleForRouteSchema {

	public ScheduleForRoute(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
