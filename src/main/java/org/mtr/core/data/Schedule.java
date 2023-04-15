package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.booleans.BooleanLongImmutablePair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2LongAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.path.PathData;
import org.mtr.core.tools.Utilities;

public class Schedule implements Utilities {

	private double timeOffsetForRepeating;
	private int departureSearchIndex;

	private final Siding siding;
	private final ObjectArrayList<Trip> trips = new ObjectArrayList<>();
	private final Long2ObjectAVLTreeMap<ObjectArraySet<Trip.StopTime>> platformTripStopTimes = new Long2ObjectAVLTreeMap<>();
	private final double railLength;
	private final TransportMode transportMode;
	private final IntArrayList departures = new IntArrayList();
	private final IntArrayList tempReturnTimes = new IntArrayList();
	private final ObjectArrayList<TimeSegment> timeSegments = new ObjectArrayList<>();

	public Schedule(Siding siding, double railLength, TransportMode transportMode) {
		this.siding = siding;
		this.railLength = railLength;
		this.transportMode = transportMode;
	}

	public void generateTimeSegments(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, double totalVehicleLength, double acceleration) {
		timeSegments.clear();
		trips.clear();

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

					if (!platformTripStopTimes.containsKey(pathData.savedRailBaseId)) {
						platformTripStopTimes.put(pathData.savedRailBaseId, new ObjectArraySet<>());
					}

					final Trip currentTrip = Utilities.getElement(trips, -1);
					if (currentTrip == null || routePlatformInfo.route.id != currentTrip.route.id) {
						final Trip trip = new Trip(routePlatformInfo.route, siding, trips.size());
						tripStopIndex = 0;
						platformTripStopTimes.get(pathData.savedRailBaseId).add(trip.addStopTime(startTime, endTime, pathData.savedRailBaseId, 0, routePlatformInfo.customDestination));
						trips.add(trip);
					} else {
						platformTripStopTimes.get(pathData.savedRailBaseId).add(currentTrip.addStopTime(startTime, endTime, pathData.savedRailBaseId, tripStopIndex, routePlatformInfo.customDestination));
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
	}

	public void startGeneratingDepartures() {
		departures.clear();
		tempReturnTimes.clear();
		for (int i = 0; i < siding.getMaxVehicles(); i++) {
			tempReturnTimes.add(0);
		}
	}

	public boolean addDeparture(int departure) {
		if (siding.getMaxVehicles() < 0) {
			return false;
		} else if (siding.getMaxVehicles() == 0) {
			departures.add(departure);
			return true;
		}

		if (!timeSegments.isEmpty() && siding.area != null) {
			final TimeSegment lastTimeSegment = Utilities.getElement(timeSegments, -1);
			for (int i = 0; i < tempReturnTimes.size(); i++) {
				if (departure >= tempReturnTimes.getInt(i)) {
					departures.add(departure);
					tempReturnTimes.set(i, siding.area.repeatInfinitely ? Integer.MAX_VALUE : departure + (int) Math.ceil(lastTimeSegment.startTime + lastTimeSegment.startSpeed / lastTimeSegment.acceleration));
					return true;
				}
			}
		}

		return false;
	}

	public void reset() {
		timeSegments.clear();
	}

	public int getDepartureCount() {
		return departures.size();
	}

	public double getTimeAlongRoute(double railProgress) {
		final int index = Utilities.getIndexFromConditionalList(timeSegments, railProgress);
		return index < 0 ? -1 : timeSegments.get(index).getTimeAlongRoute(railProgress);
	}

	public int matchDeparture() {
		final long repeatInterval = getRepeatInterval(0);
		final long offset = departures.isEmpty() || repeatInterval == 0 ? 0 : (System.currentTimeMillis() - departures.getInt(0)) / repeatInterval * repeatInterval;

		for (int i = 0; i < departures.size(); i++) {
			if (departureSearchIndex >= departures.size()) {
				departureSearchIndex = 0;
			}

			final int match = siding.simulator.matchMillis(departures.getInt(departureSearchIndex) + offset);

			if (match == 0) {
				return departureSearchIndex;
			} else if (match < 0) {
				departureSearchIndex++;
			} else {
				departureSearchIndex = 0;
			}
		}

		return -1;
	}

	public void getOBAArrivalsAndDeparturesElementsWithTripsUsed(long currentMillis, Platform platform, int millsBefore, int millisAfter, JsonArray arrivalsAndDeparturesArray, JsonArray tripsUsedArray) {
		if (siding.area == null || departures.isEmpty()) {
			return;
		}

		final ObjectArraySet<Trip.StopTime> tripStopTimes = platformTripStopTimes.get(platform.id);
		if (tripStopTimes == null) {
			return;
		}

		final long repeatInterval = getRepeatInterval(MILLIS_PER_DAY);
		final Int2LongAVLTreeMap timesAlongRoute = siding.getTimesAlongRoute();
		final ObjectAVLTreeSet<String> addedTripIds = new ObjectAVLTreeSet<>();

		tripStopTimes.forEach(stopTime -> {
			for (int departureIndex = 0; departureIndex < departures.size(); departureIndex++) {
				final int departure = departures.getInt(departureIndex);
				long departureOffset = (currentMillis - millsBefore - repeatInterval / 2 - stopTime.endTime - departure) / repeatInterval + 1;
				final ObjectObjectImmutablePair<JsonObject, BooleanLongImmutablePair> vehicleStatusWithDeviation = getOBATripStatusWithDeviation(currentMillis, stopTime, timesAlongRoute, departureIndex, departureOffset, platform.getHexId(), platform.getHexId());
				final boolean predicted = vehicleStatusWithDeviation.right().leftBoolean();
				final long deviation = vehicleStatusWithDeviation.right().rightLong();
				final Trip trip = stopTime.trip;

				while (true) {
					final String tripId = trip.getTripId(departureIndex, departureOffset);
					final long scheduledArrivalTime;
					final long scheduledDepartureTime;

					if (siding.transportMode.continuousMovement) {
						scheduledArrivalTime = currentMillis + Depot.CONTINUOUS_MOVEMENT_FREQUENCY;
						scheduledDepartureTime = currentMillis + Depot.CONTINUOUS_MOVEMENT_FREQUENCY;
					} else {
						final long newOffset = repeatInterval * departureOffset;
						scheduledArrivalTime = stopTime.startTime + newOffset + departure;
						scheduledDepartureTime = stopTime.endTime + newOffset + departure;
					}

					departureOffset++;

					if (scheduledArrivalTime > currentMillis + millisAfter + repeatInterval / 2) {
						break;
					} else if (scheduledDepartureTime + deviation < currentMillis - millsBefore || scheduledArrivalTime + deviation > currentMillis + millisAfter) {
						continue;
					}

					final long predictedArrivalTime;
					final long predictedDepartureTime;
					if (predicted) {
						predictedArrivalTime = scheduledArrivalTime + deviation;
						predictedDepartureTime = scheduledDepartureTime + deviation;
					} else {
						predictedArrivalTime = 0;
						predictedDepartureTime = 0;
					}

					final JsonObject arrivalAndDepartureObject = new JsonObject();
					arrivalAndDepartureObject.addProperty("arrivalEnabled", stopTime.tripStopIndex > 0);
					arrivalAndDepartureObject.addProperty("blockTripSequence", trip.tripIndexInBlock);
					arrivalAndDepartureObject.addProperty("departureEnabled", stopTime.tripStopIndex < trip.route.platformIds.size() - 1);
					arrivalAndDepartureObject.addProperty("distanceFromStop", 0);
					arrivalAndDepartureObject.add("frequency", siding.getOBAFrequencyElement(currentMillis));
					arrivalAndDepartureObject.addProperty("historicalOccupancy", "");
					arrivalAndDepartureObject.addProperty("lastUpdateTime", currentMillis);
					arrivalAndDepartureObject.addProperty("numberOfStopsAway", 0);
					arrivalAndDepartureObject.addProperty("occupancyStatus", "");
					arrivalAndDepartureObject.addProperty("predicted", predicted);
					arrivalAndDepartureObject.add("predictedArrivalInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("predictedArrivalTime", predictedArrivalTime);
					arrivalAndDepartureObject.add("predictedDepartureInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("predictedDepartureTime", predictedDepartureTime);
					arrivalAndDepartureObject.addProperty("predictedOccupancy", "");
					arrivalAndDepartureObject.addProperty("routeId", trip.route.getColorHex());
					arrivalAndDepartureObject.addProperty("routeLongName", trip.route.getTrimmedRouteName());
					arrivalAndDepartureObject.addProperty("routeShortName", trip.route.getFormattedRouteNumber());
					arrivalAndDepartureObject.add("scheduledArrivalInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("scheduledArrivalTime", scheduledArrivalTime);
					arrivalAndDepartureObject.add("scheduledDepartureInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("scheduledDepartureTime", scheduledDepartureTime);
					arrivalAndDepartureObject.addProperty("serviceDate", 0);
					arrivalAndDepartureObject.add("situationIds", new JsonArray());
					arrivalAndDepartureObject.addProperty("status", "default");
					arrivalAndDepartureObject.addProperty("stopId", platform.getHexId());
					arrivalAndDepartureObject.addProperty("stopSequence", stopTime.tripStopIndex);
					arrivalAndDepartureObject.addProperty("totalStopsInTrip", trip.route.platformIds.size());
					arrivalAndDepartureObject.addProperty("tripHeadsign", stopTime.customDestination);
					arrivalAndDepartureObject.addProperty("tripId", tripId);
					arrivalAndDepartureObject.add("tripStatus", vehicleStatusWithDeviation.left());
					arrivalAndDepartureObject.addProperty("vehicleId", "");
					arrivalsAndDeparturesArray.add(arrivalAndDepartureObject);

					if (!addedTripIds.contains(tripId)) {
						tripsUsedArray.add(trip.getOBATripElement(departureIndex, departureOffset));
						addedTripIds.add(tripId);
					}

					if (transportMode.continuousMovement) {
						break;
					}
				}
			}
		});
	}

	public JsonObject getOBATripDetailsWithDataUsed(long currentMillis, int tripIndex, int departureIndex, long departureOffset, LongArraySet platformIdsUsed, JsonArray tripsUsedArray) {
		final Trip trip = Utilities.getElement(trips, tripIndex);
		return trip == null ? null : trip.getOBATripDetailsWithDataUsed(
				currentMillis,
				(departureIndex < departures.size() ? departures.getInt(departureIndex) : 0) + departureOffset * getRepeatInterval(MILLIS_PER_DAY),
				departureIndex,
				departureOffset,
				Utilities.getElement(trips, tripIndex + 1),
				Utilities.getElement(trips, tripIndex - 1),
				platformIdsUsed,
				tripsUsedArray
		);
	}

	public ObjectObjectImmutablePair<JsonObject, BooleanLongImmutablePair> getOBATripStatusWithDeviation(long currentMillis, Trip.StopTime stopTime, Int2LongAVLTreeMap timesAlongRoute, int departureIndex, long departureOffset, String closestStop, String nextStop) {
		final boolean predicted;
		final long deviation;

		if (siding.transportMode.continuousMovement) {
			predicted = true;
			deviation = 0;
		} else {
			final long timeAlongRoute = timesAlongRoute.getOrDefault(departureIndex, -1);
			predicted = timeAlongRoute >= 0;
			deviation = predicted ? Utilities.circularDifference(currentMillis + stopTime.startTime - timeAlongRoute, stopTime.startTime, getRepeatInterval(MILLIS_PER_DAY)) : 0;
		}

		final JsonObject positionObject = new JsonObject();
		positionObject.addProperty("lat", 0);
		positionObject.addProperty("lon", 0);

		final JsonObject tripStatusObject = new JsonObject();
		tripStatusObject.addProperty("activeTripId", stopTime.trip.getTripId(departureIndex, departureOffset));
		tripStatusObject.addProperty("blockTripSequence", stopTime.trip.tripIndexInBlock);
		tripStatusObject.addProperty("closestStop", closestStop);
		tripStatusObject.addProperty("closestStopTimeOffset", 1);
		tripStatusObject.addProperty("distanceAlongTrip", 0);
		tripStatusObject.add("frequency", siding.getOBAFrequencyElement(currentMillis));
		tripStatusObject.addProperty("lastKnownDistanceAlongTrip", 0);
		tripStatusObject.addProperty("lastKnownOrientation", 0);
		tripStatusObject.addProperty("lastLocationUpdateTime", currentMillis);
		tripStatusObject.addProperty("lastUpdateTime", currentMillis);
		tripStatusObject.addProperty("nextStop", nextStop);
		tripStatusObject.addProperty("nextStopTimeOffset", 1);
		tripStatusObject.addProperty("occupancyCapacity", 0);
		tripStatusObject.addProperty("occupancyCount", 0);
		tripStatusObject.addProperty("occupancyStatus", "");
		tripStatusObject.addProperty("orientation", 0);
		tripStatusObject.addProperty("phase", "");
		tripStatusObject.add("position", positionObject);
		tripStatusObject.addProperty("predicted", predicted);
		tripStatusObject.addProperty("scheduleDeviation", deviation / MILLIS_PER_SECOND);
		tripStatusObject.addProperty("scheduledDistanceAlongTrip", 0);
		tripStatusObject.addProperty("serviceDate", 0);
		tripStatusObject.add("situationIds", new JsonArray());
		tripStatusObject.addProperty("status", "default");
		tripStatusObject.addProperty("totalDistanceAlongTrip", 0);
		tripStatusObject.addProperty("vehicleId", "");

		return new ObjectObjectImmutablePair<>(tripStatusObject, new BooleanLongImmutablePair(predicted, deviation));
	}

	private long getRepeatInterval(long defaultAmount) {
		final Depot depot = siding.area;
		if (depot == null) {
			return defaultAmount;
		} else {
			return siding.transportMode.continuousMovement ? Depot.CONTINUOUS_MOVEMENT_FREQUENCY : depot.repeatInfinitely ? Math.round(timeOffsetForRepeating) : depot.useRealTime ? defaultAmount : siding.simulator.millisPerGameDay;
		}
	}

	private static class RoutePlatformInfo {

		private final Route route;
		private final long platformId;
		private final String customDestination;

		private RoutePlatformInfo(Route route, long platformId, String customDestination) {
			this.route = route;
			this.platformId = platformId;
			this.customDestination = customDestination == null ? "" : customDestination.replace("|", " ");
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
