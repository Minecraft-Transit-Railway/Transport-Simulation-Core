package org.mtr.core.oba;

import org.mtr.core.generated.oba.TripDetailsSchema;

import javax.annotation.Nullable;

public final class TripDetails extends TripDetailsSchema {

	public TripDetails(String tripId, TripStatus status, Schedule schedule, @Nullable Frequency frequency) {
		super(tripId, 0, status, schedule);
		this.frequency = frequency;
	}

	@Override
	protected Frequency getDefaultFrequency() {
		return null;
	}
}
