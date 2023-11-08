package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopCalendarDaySchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopCalendarDay extends StopCalendarDaySchema {

	public StopCalendarDay(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
