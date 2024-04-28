package org.mtr.core.data;

import org.mtr.core.oba.Schedule;
import org.mtr.core.oba.SingleElement;
import org.mtr.core.oba.TripDetails;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class Trip implements Utilities {

	public final Route route;
	public final int routeIndex;
	public final int tripIndexInBlock;
	private final Siding siding;
	private final String tripIdPrefix;
	private final ObjectArrayList<StopTime> stopTimes = new ObjectArrayList<>();

	public Trip(Route route, int routeIndex, int tripIndexInBlock, Siding siding) {
		this.route = route;
		this.routeIndex = routeIndex;
		this.tripIndexInBlock = tripIndexInBlock;
		this.siding = siding;
		tripIdPrefix = String.format("%s_%s", siding.getHexId(), tripIndexInBlock);
	}

	public StopTime addStopTime(long startTime, long endTime, long platformId, int tripStopIndex, String customDestination) {
		final StopTime stopTime = new StopTime(this, startTime, endTime, platformId, tripStopIndex, customDestination);
		stopTimes.add(stopTime);
		return stopTime;
	}

	public String getTripId(int departureIndex, long departureOffset) {
		return Utilities.concat(tripIdPrefix, "_", departureIndex, "_", departureOffset);
	}

	public void getUpcomingStopTimes(int tripStopIndex, ObjectArrayList<Trip> trips, boolean repeatIndefinitely, Consumer<StopTime> consumer) {
		final StopTime stopTime1 = Utilities.getElement(stopTimes, tripStopIndex);
		if (stopTime1 == null) {
			return;
		}

		final int tripsCount = trips.size();
		int tempTripIndex = tripIndexInBlock;
		int tempTripStopIndex = tripStopIndex + 1;

		while (true) {
			final Trip trip = trips.get(tempTripIndex % tripsCount);

			if (tempTripStopIndex < trip.stopTimes.size()) {
				final StopTime stopTime2 = trip.stopTimes.get(tempTripStopIndex);
				if (stopTime1.platformId != stopTime2.platformId) {
					consumer.accept(stopTime2);
					break;
				}
				tempTripStopIndex++;
			} else {
				tempTripIndex++;
				tempTripStopIndex = 0;
			}

			final boolean shouldBreak1 = !repeatIndefinitely && tempTripIndex >= tripsCount;
			final boolean shouldBreak2 = repeatIndefinitely && tempTripIndex >= tripIndexInBlock + tripsCount && tempTripStopIndex >= tripStopIndex;
			if (shouldBreak1 || shouldBreak2) {
				break;
			}
		}
	}

	public void getOBATripDetailsWithDataUsed(SingleElement<TripDetails> singleElement, long currentMillis, long offsetMillis, int departureIndex, long departureOffset, @Nullable Trip nextTrip, @Nullable Trip previousTrip) {
		if (stopTimes.isEmpty()) {
			return;
		}

		singleElement.addTrip(getOBATripElement(departureIndex, departureOffset));
		if (nextTrip != null) {
			singleElement.addTrip(nextTrip.getOBATripElement(departureIndex, departureOffset));
		}
		if (previousTrip != null) {
			singleElement.addTrip(previousTrip.getOBATripElement(departureIndex, departureOffset));
		}

		final Schedule schedule = new Schedule(
				previousTrip == null ? "" : previousTrip.getTripId(departureIndex, departureOffset),
				nextTrip == null ? "" : nextTrip.getTripId(departureIndex, departureOffset),
				siding.getOBAFrequencyElement(currentMillis)
		);

		stopTimes.forEach(tripStopTime -> {
			schedule.addStopTime(new org.mtr.core.oba.StopTime(tripStopTime, offsetMillis));
			singleElement.addStop(tripStopTime.platformId);
		});

		singleElement.set(new TripDetails(
				getTripId(departureIndex, departureOffset),
				siding.getOBATripStatus(currentMillis, stopTimes.get(0), departureIndex, departureOffset, "", ""),
				schedule,
				siding.getOBAFrequencyElement(currentMillis)
		));
	}

	public org.mtr.core.oba.Trip getOBATripElement(String tripId, int departureIndex) {
		return new org.mtr.core.oba.Trip(route, tripId, departureIndex);
	}

	private org.mtr.core.oba.Trip getOBATripElement(int departureIndex, long departureOffset) {
		return getOBATripElement(getTripId(departureIndex, departureOffset), departureIndex);
	}

	public static class StopTime {

		public final Trip trip;
		public final long startTime;
		public final long endTime;
		public final long platformId;
		public final int tripStopIndex;
		public final String customDestination;

		private StopTime(Trip trip, long startTime, long endTime, long platformId, int tripStopIndex, String customDestination) {
			this.trip = trip;
			this.startTime = startTime;
			this.endTime = endTime;
			this.platformId = platformId;
			this.tripStopIndex = tripStopIndex;
			this.customDestination = customDestination;
		}
	}
}
