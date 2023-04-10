package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.path.PathData;
import org.mtr.core.tools.Utilities;

import java.util.TimeZone;

public class Schedule implements Utilities {

	private double timeOffsetForRepeating;

	public final ObjectArrayList<ObjectArrayList<Trip>> blocks = new ObjectArrayList<>();
	private final double railLength;
	private final TransportMode transportMode;
	private final ObjectArrayList<TimeSegment> timeSegments = new ObjectArrayList<>();

	public Schedule(double railLength, TransportMode transportMode) {
		this.railLength = railLength;
		this.transportMode = transportMode;
	}

	public void generateTimeSegments(Siding siding, ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, double totalVehicleLength, double acceleration) {
		timeSegments.clear();
		blocks.clear();

		final Depot depot = siding.area;
		final ObjectArrayList<PathData> path = new ObjectArrayList<>();
		path.addAll(pathSidingToMainRoute);
		path.addAll(pathMainRoute);

		final double totalDistance = Utilities.getElement(path, -1).endDistance;
		final DoubleArrayList stoppingDistances = new DoubleArrayList();
		for (final PathData pathData : path) {
			if (pathData.dwellTimeMillis > 0) {
				stoppingDistances.add(pathData.endDistance);
			}
		}

		final ObjectArrayList<RoutePlatformInfo> routePlatformInfoList = new ObjectArrayList<>();
		depot.iterateRoutes(route -> {
			for (int i = 0; i < route.platformIds.size(); i++) {
				routePlatformInfoList.add(new RoutePlatformInfo(route, route.platformIds.get(i).platformId, route.getDestination(siding.simulator.dataCache, i)));
			}
		});
		final ObjectArrayList<Trip> trips = new ObjectArrayList<>();

		double railProgress = (railLength + totalVehicleLength) / 2;
		double nextStoppingDistance = 0;
		double speed = 0;
		double time = 0;
		int tripStopIndex = 0;
		for (int i = 0; i < path.size(); i++) {
			if (railProgress >= nextStoppingDistance) {
				if (stoppingDistances.isEmpty()) {
					nextStoppingDistance = totalDistance;
				} else {
					nextStoppingDistance = stoppingDistances.removeDouble(0);
				}
			}

			if (i == pathSidingToMainRoute.size()) {
				timeOffsetForRepeating = time; // TODO slight inaccuracy if vehicle length is different from the first platform length
			}

			final PathData pathData = path.get(i);
			final double railSpeed = pathData.rail.canAccelerate ? pathData.rail.speedLimitMetersPerMillisecond : Math.max(speed, transportMode.defaultSpeedMetersPerMillisecond);
			final double currentDistance = pathData.endDistance;

			while (railProgress < currentDistance) {
				final int speedChange;
				if (speed > railSpeed || nextStoppingDistance - railProgress + 1 < 0.5 * speed * speed / acceleration) {
					speed = Math.max(speed - acceleration, acceleration);
					speedChange = -1;
				} else if (speed < railSpeed) {
					speed = Math.min(speed + acceleration, railSpeed);
					speedChange = 1;
				} else {
					speedChange = 0;
				}

				if (timeSegments.isEmpty() || Utilities.getElement(timeSegments, -1).speedChange != speedChange) {
					timeSegments.add(new TimeSegment(railProgress, speed, time, speedChange, acceleration));
				}

				railProgress = Math.min(railProgress + speed, currentDistance);
				time++;
			}

			if (pathData.savedRailBaseId != 0) {
				final long startTime = Math.round(time);
				time += pathData.dwellTimeMillis;
				final long endTime = Math.round(time);

				while (!routePlatformInfoList.isEmpty()) {
					final RoutePlatformInfo routePlatformInfo = routePlatformInfoList.get(0);

					if (routePlatformInfo.platformId != pathData.savedRailBaseId) {
						break;
					}

					final Trip currentTrip = Utilities.getElement(trips, -1);
					if (currentTrip == null || routePlatformInfo.route.id != currentTrip.route.id) {
						final Trip trip = new Trip(routePlatformInfo.route, siding, trips.size());
						tripStopIndex = 0;
						trip.stopTimes.add(new TripStopInfo(startTime, endTime, pathData.savedRailBaseId, 0, routePlatformInfo.customDestination));
						trips.add(trip);
					} else {
						currentTrip.stopTimes.add(new TripStopInfo(startTime, endTime, pathData.savedRailBaseId, tripStopIndex, routePlatformInfo.customDestination));
					}

					tripStopIndex++;
					routePlatformInfoList.remove(0);
				}
			} else {
				time += pathData.dwellTimeMillis;
			}

			if (i + 1 < path.size() && pathData.isOppositeRail(path.get(i + 1))) {
				railProgress += totalVehicleLength;
			}
		}

		timeOffsetForRepeating = time - timeOffsetForRepeating;

		depot.getDepartures(siding.id).forEach(departureTimeOffset -> {
			final ObjectArrayList<Trip> newTrips = new ObjectArrayList<>();
			trips.forEach(trip -> newTrips.add(new Trip(trip, departureTimeOffset)));
			blocks.add(newTrips);
		});
	}

	public void reset() {
		timeSegments.clear();
	}

	public double getTimeAlongRoute(double railProgress) {
		final int index = Utilities.getIndexFromConditionalList(timeSegments, railProgress);
		return index < 0 ? -1 : timeSegments.get(index).getTimeAlongRoute(railProgress);
	}

	public static class Trip {

		private final Route route;
		private final Siding siding;
		private final int tripIndexInBlock;
		private final int departureTimeOffset;
		private final ObjectArrayList<TripStopInfo> stopTimes = new ObjectArrayList<>();

		private Trip(Route route, Siding siding, int tripIndexInBlock) {
			this.route = route;
			this.siding = siding;
			this.tripIndexInBlock = tripIndexInBlock;
			this.departureTimeOffset = 0;
		}

		private Trip(Trip trip, int departureTimeOffset) {
			route = trip.route;
			siding = trip.siding;
			tripIndexInBlock = trip.tripIndexInBlock;
			this.departureTimeOffset = departureTimeOffset;
			trip.stopTimes.forEach(stopTime -> stopTimes.add(new TripStopInfo(
					stopTime.startTime + departureTimeOffset,
					stopTime.endTime + departureTimeOffset,
					stopTime.platformId,
					stopTime.tripStopIndex,
					stopTime.customDestination
			)));
		}

		public String getId() {
			return String.format("%s_%s_%s_%s", route.getColorHex(), siding.getHexId(), tripIndexInBlock, departureTimeOffset);
		}

		public void getOBAArrivalsAndDeparturesElementsWithTripsUsed(Platform platform, int minutesBefore, int minutesAfter, JsonArray arrivalsAndDeparturesArray, JsonArray tripsUsedArray) {
			final long currentMillis = System.currentTimeMillis();
			final long startOfDayMillis = currentMillis - (currentMillis % Utilities.MILLIS_PER_DAY);
			boolean needToAddTrip = true;

			for (final TripStopInfo tripStopInfo : stopTimes) {
				final long scheduledArrivalTime = startOfDayMillis + tripStopInfo.startTime;
				final long scheduledDepartureTime = startOfDayMillis + tripStopInfo.endTime;

				if (tripStopInfo.platformId == platform.id && currentMillis >= scheduledDepartureTime - minutesBefore * 60000L && currentMillis <= scheduledArrivalTime + minutesAfter * 60000L) {
					final JsonObject positionObject = new JsonObject();
					positionObject.addProperty("lat", 0);
					positionObject.addProperty("lon", 0);

					final JsonObject tripStatusObject = new JsonObject();
					tripStatusObject.addProperty("activeTripId", getId());
					tripStatusObject.addProperty("blockTripSequence", tripIndexInBlock);
					tripStatusObject.addProperty("closestStop", platform.getHexId());
					tripStatusObject.addProperty("closestStopTimeOffset", 1);
					tripStatusObject.addProperty("distanceAlongTrip", 0);
					tripStatusObject.add("frequency", JsonNull.INSTANCE);
					tripStatusObject.addProperty("lastKnownDistanceAlongTrip", 0);
					tripStatusObject.addProperty("lastKnownOrientation", 0);
					tripStatusObject.addProperty("lastLocationUpdateTime", 0);
					tripStatusObject.addProperty("lastUpdateTime", 0);
					tripStatusObject.addProperty("nextStop", platform.getHexId());
					tripStatusObject.addProperty("nextStopTimeOffset", 1);
					tripStatusObject.addProperty("occupancyCapacity", 0);
					tripStatusObject.addProperty("occupancyCount", 0);
					tripStatusObject.addProperty("occupancyStatus", "");
					tripStatusObject.addProperty("orientation", 0);
					tripStatusObject.addProperty("phase", "");
					tripStatusObject.add("position", positionObject);
					tripStatusObject.addProperty("predicted", false);
					tripStatusObject.addProperty("scheduleDeviation", 0);
					tripStatusObject.addProperty("scheduledDistanceAlongTrip", 0);
					tripStatusObject.addProperty("serviceDate", startOfDayMillis);
					tripStatusObject.add("situationIds", new JsonArray());
					tripStatusObject.addProperty("status", "default");
					tripStatusObject.addProperty("totalDistanceAlongTrip", 0);
					tripStatusObject.addProperty("vehicleId", "");

					final JsonObject arrivalAndDepartureObject = new JsonObject();
					arrivalAndDepartureObject.addProperty("arrivalEnabled", tripStopInfo.tripStopIndex > 0);
					arrivalAndDepartureObject.addProperty("blockTripSequence", tripIndexInBlock);
					arrivalAndDepartureObject.addProperty("departureEnabled", tripStopInfo.tripStopIndex < route.platformIds.size() - 1);
					arrivalAndDepartureObject.addProperty("distanceFromStop", 0);
					arrivalAndDepartureObject.add("frequency", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("historicalOccupancy", "");
					arrivalAndDepartureObject.addProperty("lastUpdateTime", 0);
					arrivalAndDepartureObject.addProperty("numberOfStopsAway", 0);
					arrivalAndDepartureObject.addProperty("occupancyStatus", "");
					arrivalAndDepartureObject.addProperty("predicted", false);
					arrivalAndDepartureObject.add("predictedArrivalInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("predictedArrivalTime", 0);
					arrivalAndDepartureObject.add("predictedDepartureInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("predictedDepartureTime", 0);
					arrivalAndDepartureObject.addProperty("predictedOccupancy", "");
					arrivalAndDepartureObject.addProperty("routeId", route.getColorHex());
					arrivalAndDepartureObject.addProperty("routeLongName", route.getTrimmedRouteName());
					arrivalAndDepartureObject.addProperty("routeShortName", route.getFormattedRouteNumber());
					arrivalAndDepartureObject.add("scheduledArrivalInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("scheduledArrivalTime", scheduledArrivalTime);
					arrivalAndDepartureObject.add("scheduledDepartureInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("scheduledDepartureTime", scheduledDepartureTime);
					arrivalAndDepartureObject.addProperty("serviceDate", startOfDayMillis);
					arrivalAndDepartureObject.add("situationIds", new JsonArray());
					arrivalAndDepartureObject.addProperty("status", "default");
					arrivalAndDepartureObject.addProperty("stopId", platform.getHexId());
					arrivalAndDepartureObject.addProperty("stopSequence", tripStopInfo.tripStopIndex);
					arrivalAndDepartureObject.addProperty("totalStopsInTrip", route.platformIds.size());
					arrivalAndDepartureObject.addProperty("tripHeadsign", tripStopInfo.customDestination == null ? "destination" : tripStopInfo.customDestination.replace("|", " "));
					arrivalAndDepartureObject.addProperty("tripId", getId());
					arrivalAndDepartureObject.add("tripStatus", tripStatusObject);
					arrivalAndDepartureObject.addProperty("vehicleId", "");
					arrivalsAndDeparturesArray.add(arrivalAndDepartureObject);

					if (needToAddTrip) {
						tripsUsedArray.add(getOBATripElement());
						needToAddTrip = false;
					}
				}
			}
		}

		public JsonObject getOBATripElement() {
			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("blockId", String.valueOf(departureTimeOffset));
			jsonObject.addProperty("directionId", 0);
			jsonObject.addProperty("id", getId());
			jsonObject.addProperty("routeId", route.getColorHex());
			jsonObject.addProperty("routeShortName", route.getFormattedRouteNumber());
			jsonObject.addProperty("serviceId", "1");
			jsonObject.addProperty("shapeId", "");
			jsonObject.addProperty("timeZone", TimeZone.getDefault().getID());
			jsonObject.addProperty("tripHeadsign", "");
			jsonObject.addProperty("tripShortName", route.getFormattedRouteNumber());
			return jsonObject;
		}
	}

	private static class RoutePlatformInfo {

		private final Route route;
		private final long platformId;
		private final String customDestination;

		private RoutePlatformInfo(Route route, long platformId, String customDestination) {
			this.route = route;
			this.platformId = platformId;
			this.customDestination = customDestination;
		}
	}

	private static class TripStopInfo {

		private final long startTime;
		private final long endTime;
		private final long platformId;
		private final int tripStopIndex;
		private final String customDestination;

		private TripStopInfo(long startTime, long endTime, long platformId, int tripStopIndex, String customDestination) {
			this.startTime = startTime;
			this.endTime = endTime;
			this.platformId = platformId;
			this.tripStopIndex = tripStopIndex;
			this.customDestination = customDestination;
		}
	}

	private static class TimeSegment implements ConditionalList {

		private final double startRailProgress;
		private final double startSpeed;
		private final double startTime;
		private final int speedChange;
		private final double acceleration;

		private TimeSegment(double startRailProgress, double startSpeed, double startTime, int speedChange, double acceleration) {
			this.startRailProgress = startRailProgress;
			this.startSpeed = startSpeed;
			this.startTime = startTime;
			this.speedChange = Integer.compare(speedChange, 0);
			this.acceleration = Train.roundAcceleration(acceleration);
		}

		@Override
		public boolean matchesCondition(double value) {
			return value >= startRailProgress;
		}

		private double getTimeAlongRoute(double railProgress) {
			final double distance = railProgress - startRailProgress;
			if (speedChange == 0) {
				return startTime + distance / startSpeed;
			} else {
				final double totalAcceleration = speedChange * acceleration;
				return startTime + (distance == 0 ? 0 : (Math.sqrt(2 * totalAcceleration * distance + startSpeed * startSpeed) - startSpeed) / totalAcceleration);
			}
		}
	}
}
