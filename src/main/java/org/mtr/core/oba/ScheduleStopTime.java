package org.mtr.core.oba;

import org.mtr.core.generated.oba.ScheduleStopTimeSchema;
import org.mtr.core.serializer.ReaderBase;

public final class ScheduleStopTime extends ScheduleStopTimeSchema {

	public ScheduleStopTime(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
