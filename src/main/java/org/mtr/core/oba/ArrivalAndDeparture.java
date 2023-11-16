package org.mtr.core.oba;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Trip;
import org.mtr.core.generated.oba.ArrivalAndDepartureSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;

public final class ArrivalAndDeparture extends ArrivalAndDepartureSchema implements Comparable<ArrivalAndDeparture> {

	public static ArrivalAndDeparture create(
			Trip trip,
			String tripId,
			Platform platform,
			Trip.StopTime stopTime,
			long scheduledArrivalTime,
			long scheduledDepartureTime,
			boolean predicted,
			long deviation,
			OccupancyStatus occupancyStatus,
			String vehicleId,
			@Nullable Frequency frequency,
			TripStatus tripStatus
	) {
		return new ArrivalAndDeparture(trip, tripId, platform, stopTime, stopTime.tripStopIndex == trip.route.getRoutePlatforms().size() - 1, scheduledArrivalTime, scheduledDepartureTime, predicted, deviation, occupancyStatus, vehicleId, frequency, tripStatus);
	}

	private ArrivalAndDeparture(
			Trip trip,
			String tripId,
			Platform platform,
			Trip.StopTime stopTime,
			boolean isTerminating,
			long scheduledArrivalTime,
			long scheduledDepartureTime,
			boolean predicted,
			long deviation,
			OccupancyStatus occupancyStatus,
			String vehicleId,
			@Nullable Frequency frequency,
			TripStatus tripStatus
	) {
		super(
				trip.route.getColorHex(),
				tripId,
				0,
				platform.getHexId(),
				stopTime.tripStopIndex,
				trip.route.getRoutePlatforms().size(),
				trip.tripIndexInBlock,
				Utilities.formatName(trip.route.getRouteNumber()),
				Utilities.formatName(trip.route.getName()),
				(isTerminating ? "(Terminating) " : "") + stopTime.customDestination,
				stopTime.tripStopIndex > 0,
				!isTerminating,
				scheduledArrivalTime,
				scheduledDepartureTime,
				predicted,
				predicted ? scheduledArrivalTime + deviation : 0,
				predicted ? scheduledDepartureTime + deviation : 0,
				0,
				OccupancyStatus.MANY_SEATS_AVAILABLE,
				0,
				occupancyStatus,
				"default",
				vehicleId
		);
		this.frequency = frequency;
		this.tripStatus = tripStatus;
	}

	public ArrivalAndDeparture(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	protected Frequency getDefaultFrequency() {
		return null;
	}

	@Override
	protected TripStatus getDefaultTripStatus() {
		return null;
	}

	@Override
	public int compareTo(ArrivalAndDeparture arrivalAndDeparture) {
		return Utilities.compare(predicted ? predictedArrivalTime : scheduledArrivalTime, arrivalAndDeparture.predicted ? arrivalAndDeparture.predictedArrivalTime : arrivalAndDeparture.scheduledArrivalTime, () -> Utilities.compare(tripHeadsign, arrivalAndDeparture.tripHeadsign, () -> 0));
	}
}
