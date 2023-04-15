package org.mtr.core.data;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.tools.Utilities;

import java.util.TimeZone;

public class Trip implements Utilities {

	public final Route route;
	private final Siding siding;
	public final int tripIndexInBlock;
	private final ObjectArrayList<StopTime> stopTimes = new ObjectArrayList<>();

	public Trip(Route route, Siding siding, int tripIndexInBlock) {
		this.route = route;
		this.siding = siding;
		this.tripIndexInBlock = tripIndexInBlock;
	}

	public StopTime addStopTime(long startTime, long endTime, long platformId, int tripStopIndex, String customDestination) {
		final StopTime stopTime = new StopTime(this, startTime, endTime, platformId, tripStopIndex, customDestination);
		stopTimes.add(stopTime);
		return stopTime;
	}

	public String getTripId(int departureIndex, long departureOffset) {
		return String.format("%s_%s_%s_%s", siding.getHexId(), tripIndexInBlock, departureIndex, departureOffset);
	}

	public JsonObject getOBATripDetailsWithDataUsed(long currentMillis, long offset, int departureIndex, long departureOffset, Trip nextTrip, Trip previousTrip, LongArraySet platformIdsUsed, JsonArray tripsUsedArray) {
		if (stopTimes.isEmpty()) {
			return null;
		}

		tripsUsedArray.add(getOBATripElement(departureIndex, departureOffset));
		if (nextTrip != null) {
			tripsUsedArray.add(nextTrip.getOBATripElement(departureIndex, departureOffset));
		}
		if (previousTrip != null) {
			tripsUsedArray.add(previousTrip.getOBATripElement(departureIndex, departureOffset));
		}

		final JsonArray stopTimesArray = new JsonArray();
		stopTimes.forEach(tripStopTime -> {
			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("arrivalTime", (tripStopTime.startTime + offset) / MILLIS_PER_SECOND);
			jsonObject.addProperty("departureTime", (tripStopTime.endTime + offset) / MILLIS_PER_SECOND);
			jsonObject.addProperty("distanceAlongTrip", 0);
			jsonObject.addProperty("historicalOccupancy", "");
			jsonObject.addProperty("stopHeadsign", tripStopTime.customDestination);
			jsonObject.addProperty("stopId", Utilities.numberToPaddedHexString(tripStopTime.platformId));
			stopTimesArray.add(jsonObject);
			platformIdsUsed.add(tripStopTime.platformId);
		});

		final JsonObject scheduleObject = new JsonObject();
		scheduleObject.add("frequency", siding.getOBAFrequencyElement(currentMillis));
		scheduleObject.addProperty("nextTripId", nextTrip == null ? "" : nextTrip.getTripId(departureIndex, departureOffset));
		scheduleObject.addProperty("previousTripId", previousTrip == null ? "" : previousTrip.getTripId(departureIndex, departureOffset));
		scheduleObject.add("stopTimes", stopTimesArray);
		scheduleObject.addProperty("timezone", TimeZone.getDefault().getID());

		final JsonObject tripDetailsObject = new JsonObject();
		tripDetailsObject.add("frequency", siding.getOBAFrequencyElement(currentMillis));
		tripDetailsObject.add("schedule", scheduleObject);
		tripDetailsObject.addProperty("serviceDate", 0);
		tripDetailsObject.add("situationIds", new JsonArray());
		tripDetailsObject.add("status", siding.schedule.getOBATripStatusWithDeviation(currentMillis, stopTimes.get(0), siding.getTimesAlongRoute(), departureIndex, departureOffset, "", "").left());
		tripDetailsObject.addProperty("tripId", getTripId(departureIndex, departureOffset));
		return tripDetailsObject;
	}

	public JsonObject getOBATripElement(int departureIndex, long departureOffset) {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("blockId", String.valueOf(departureIndex));
		jsonObject.addProperty("directionId", 0);
		jsonObject.addProperty("id", getTripId(departureIndex, departureOffset));
		jsonObject.addProperty("routeId", route.getColorHex());
		jsonObject.addProperty("routeShortName", route.getFormattedRouteNumber());
		jsonObject.addProperty("serviceId", "1");
		jsonObject.addProperty("shapeId", "");
		jsonObject.addProperty("timeZone", TimeZone.getDefault().getID());
		jsonObject.addProperty("tripHeadsign", route.getTrimmedRouteName());
		jsonObject.addProperty("tripShortName", "");
		return jsonObject;
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
