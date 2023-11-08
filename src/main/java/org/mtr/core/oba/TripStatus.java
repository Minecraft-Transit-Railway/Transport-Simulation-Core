package org.mtr.core.oba;

import org.mtr.core.data.Trip;
import org.mtr.core.generated.oba.TripStatusSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;

public final class TripStatus extends TripStatusSchema implements Utilities {

	public TripStatus(
			String tripId,
			Trip.StopTime stopTime,
			String closestStop,
			String nextStop,
			OccupancyStatus occupancyStatus,
			boolean predicted,
			long currentMillis,
			long deviation,
			String vehicleId,
			@Nullable Frequency frequency
	) {
		super(
				tripId,
				stopTime.trip.tripIndexInBlock,
				0,
				0,
				0,
				new Position(0, 0),
				0,
				closestStop,
				1,
				nextStop,
				1,
				occupancyStatus,
				"",
				"default",
				predicted,
				currentMillis,
				currentMillis,
				new Position(0, 0),
				0,
				0,
				0,
				deviation / MILLIS_PER_SECOND,
				vehicleId
		);
		this.frequency = frequency;
	}

	public TripStatus(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	protected Frequency getDefaultFrequency() {
		return null;
	}
}
