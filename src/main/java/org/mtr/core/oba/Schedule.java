package org.mtr.core.oba;

import org.mtr.core.generated.oba.ScheduleSchema;
import org.mtr.core.serializer.ReaderBase;

import javax.annotation.Nullable;
import java.util.TimeZone;

public final class Schedule extends ScheduleSchema {

	public Schedule(String previousTripId, String nextTripId, @Nullable Frequency frequency) {
		super(TimeZone.getDefault().getID(), previousTripId, nextTripId);
		this.frequency = frequency;
	}

	public Schedule(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void addStopTime(StopTime stopTime) {
		stopTimes.add(stopTime);
	}

	@Override
	protected Frequency getDefaultFrequency() {
		return null;
	}
}
