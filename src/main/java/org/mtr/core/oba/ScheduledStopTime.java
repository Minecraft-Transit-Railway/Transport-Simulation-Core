package org.mtr.core.oba;

import org.mtr.core.generated.oba.ScheduledStopTimeSchema;
import org.mtr.core.serializer.ReaderBase;

public final class ScheduledStopTime extends ScheduledStopTimeSchema {

	public ScheduledStopTime(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
