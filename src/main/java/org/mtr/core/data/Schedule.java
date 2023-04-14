package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2LongAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.path.PathData;
import org.mtr.core.tools.Utilities;

import java.util.TimeZone;

public class Schedule implements Utilities {

	private double timeOffsetForRepeating;
	private int departureSearchIndex;

	private final Siding siding;
	private final ObjectArrayList<Trip> trips = new ObjectArrayList<>();
	private final Long2ObjectAVLTreeMap<ObjectArraySet<TripStopInfo>> platformTripStopInfoSet = new Long2ObjectAVLTreeMap<>();
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

					if (!platformTripStopInfoSet.containsKey(pathData.savedRailBaseId)) {
						platformTripStopInfoSet.put(pathData.savedRailBaseId, new ObjectArraySet<>());
					}

					final Trip currentTrip = Utilities.getElement(trips, -1);
					if (currentTrip == null || routePlatformInfo.route.id != currentTrip.route.id) {
						final Trip trip = new Trip(routePlatformInfo.route, trips.size());
						tripStopIndex = 0;
						final TripStopInfo tripStopInfo = new TripStopInfo(trip, startTime, endTime, 0, routePlatformInfo.customDestination);
						trip.stopTimes.add(tripStopInfo);
						trips.add(trip);
						platformTripStopInfoSet.get(pathData.savedRailBaseId).add(tripStopInfo);
					} else {
						final TripStopInfo tripStopInfo = new TripStopInfo(currentTrip, startTime, endTime, tripStopIndex, routePlatformInfo.customDestination);
						currentTrip.stopTimes.add(tripStopInfo);
						platformTripStopInfoSet.get(pathData.savedRailBaseId).add(tripStopInfo);
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

	public void getOBAArrivalsAndDeparturesElementsWithTripsUsed(Platform platform, int millsBefore, int millisAfter, JsonArray arrivalsAndDeparturesArray, JsonArray tripsUsedArray) {
		if (siding.area == null || departures.isEmpty()) {
			return;
		}

		final ObjectArraySet<TripStopInfo> platformTripStopInfo = platformTripStopInfoSet.get(platform.id);
		if (platformTripStopInfo == null) {
			return;
		}

		final long currentMillis = System.currentTimeMillis();
		final long repeatInterval = getRepeatInterval(MILLIS_PER_DAY);
		final Int2LongAVLTreeMap timesAlongRoute = siding.transportMode.continuousMovement ? new Int2LongAVLTreeMap() : siding.getTimesAlongRoute();
		final ObjectAVLTreeSet<String> addedTripIds = new ObjectAVLTreeSet<>();

		platformTripStopInfo.forEach(tripStopInfo -> {
			for (int i = 0; i < departures.size(); i++) {
				final Trip trip = tripStopInfo.trip;
				final String tripId = String.format("%s_%s_%s_%s", trip.route.getColorHex(), siding.getHexId(), trip.tripIndexInBlock, i);
				final long initialOffset;
				final boolean predicted;
				final long deviation;
				final JsonElement frequencyObject;

				if (siding.transportMode.continuousMovement) {
					initialOffset = currentMillis - tripStopInfo.startTime;
					predicted = true;
					deviation = 0;
					frequencyObject = new JsonObject();
					((JsonObject) frequencyObject).addProperty("endTime", currentMillis + MILLIS_PER_DAY);
					((JsonObject) frequencyObject).addProperty("exactTimes", 0);
					((JsonObject) frequencyObject).addProperty("headway", Depot.CONTINUOUS_MOVEMENT_FREQUENCY / MILLIS_PER_SECOND);
					((JsonObject) frequencyObject).addProperty("startTime", 0);
				} else {
					final int departure = departures.getInt(i);
					initialOffset = ((currentMillis - millsBefore - repeatInterval / 2 - tripStopInfo.endTime - departure) / repeatInterval + 1) * repeatInterval + departure;
					final long timeAlongRoute = timesAlongRoute.getOrDefault(i, -1);
					predicted = timeAlongRoute >= 0;
					deviation = predicted ? Utilities.circularDifference(currentMillis + tripStopInfo.startTime - timeAlongRoute, tripStopInfo.startTime + initialOffset, repeatInterval) : 0;
					frequencyObject = JsonNull.INSTANCE;
				}

				int offset = 0;
				while (true) {
					final long newOffset = repeatInterval * offset;
					final long scheduledArrivalTime = tripStopInfo.startTime + initialOffset + newOffset;
					final long scheduledDepartureTime = tripStopInfo.endTime + initialOffset + newOffset;
					final long predictedArrivalTime;
					final long predictedDepartureTime;
					offset++;

					if (scheduledArrivalTime > currentMillis + millisAfter + repeatInterval / 2) {
						break;
					} else if (scheduledDepartureTime + deviation < currentMillis - millsBefore || scheduledArrivalTime + deviation > currentMillis + millisAfter) {
						continue;
					}

					if (predicted) {
						predictedArrivalTime = scheduledArrivalTime + deviation;
						predictedDepartureTime = scheduledDepartureTime + deviation;
					} else {
						predictedArrivalTime = 0;
						predictedDepartureTime = 0;
					}

					final JsonObject positionObject = new JsonObject();
					positionObject.addProperty("lat", 0);
					positionObject.addProperty("lon", 0);

					final JsonObject tripStatusObject = new JsonObject();
					tripStatusObject.addProperty("activeTripId", tripId);
					tripStatusObject.addProperty("blockTripSequence", trip.tripIndexInBlock);
					tripStatusObject.addProperty("closestStop", platform.getHexId());
					tripStatusObject.addProperty("closestStopTimeOffset", 1);
					tripStatusObject.addProperty("distanceAlongTrip", 0);
					tripStatusObject.add("frequency", frequencyObject);
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
					tripStatusObject.addProperty("predicted", predicted);
					tripStatusObject.addProperty("scheduleDeviation", deviation);
					tripStatusObject.addProperty("scheduledDistanceAlongTrip", 0);
					tripStatusObject.addProperty("serviceDate", 0);
					tripStatusObject.add("situationIds", new JsonArray());
					tripStatusObject.addProperty("status", "default");
					tripStatusObject.addProperty("totalDistanceAlongTrip", 0);
					tripStatusObject.addProperty("vehicleId", "");

					final JsonObject arrivalAndDepartureObject = new JsonObject();
					arrivalAndDepartureObject.addProperty("arrivalEnabled", tripStopInfo.tripStopIndex > 0);
					arrivalAndDepartureObject.addProperty("blockTripSequence", trip.tripIndexInBlock);
					arrivalAndDepartureObject.addProperty("departureEnabled", tripStopInfo.tripStopIndex < trip.route.platformIds.size() - 1);
					arrivalAndDepartureObject.addProperty("distanceFromStop", 0);
					arrivalAndDepartureObject.add("frequency", frequencyObject);
					arrivalAndDepartureObject.addProperty("historicalOccupancy", "");
					arrivalAndDepartureObject.addProperty("lastUpdateTime", 0);
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
					arrivalAndDepartureObject.addProperty("stopSequence", tripStopInfo.tripStopIndex);
					arrivalAndDepartureObject.addProperty("totalStopsInTrip", trip.route.platformIds.size());
					arrivalAndDepartureObject.addProperty("tripHeadsign", tripStopInfo.customDestination == null ? "" : tripStopInfo.customDestination.replace("|", " "));
					arrivalAndDepartureObject.addProperty("tripId", tripId);
					arrivalAndDepartureObject.add("tripStatus", tripStatusObject);
					arrivalAndDepartureObject.addProperty("vehicleId", "");
					arrivalsAndDeparturesArray.add(arrivalAndDepartureObject);

					if (transportMode.continuousMovement) {
						break;
					}
				}

				if (!addedTripIds.contains(tripId)) {
					final JsonObject tripObject = new JsonObject();
					tripObject.addProperty("blockId", String.valueOf(i));
					tripObject.addProperty("directionId", 0);
					tripObject.addProperty("id", tripId);
					tripObject.addProperty("routeId", trip.route.getColorHex());
					tripObject.addProperty("routeShortName", trip.route.getFormattedRouteNumber());
					tripObject.addProperty("serviceId", "1");
					tripObject.addProperty("shapeId", "");
					tripObject.addProperty("timeZone", TimeZone.getDefault().getID());
					tripObject.addProperty("tripHeadsign", "");
					tripObject.addProperty("tripShortName", trip.route.getFormattedRouteNumber());
					tripsUsedArray.add(tripObject);
					addedTripIds.add(tripId);
				}
			}
		});
	}

	private long getRepeatInterval(long defaultAmount) {
		final Depot depot = siding.area;
		if (depot == null) {
			return defaultAmount;
		} else {
			return siding.transportMode.continuousMovement ? Depot.CONTINUOUS_MOVEMENT_FREQUENCY : depot.repeatInfinitely ? Math.round(timeOffsetForRepeating) : depot.useRealTime ? defaultAmount : siding.simulator.millisPerGameDay;
		}
	}

	private static class Trip {

		private final Route route;
		private final int tripIndexInBlock;
		private final ObjectArrayList<TripStopInfo> stopTimes = new ObjectArrayList<>();

		private Trip(Route route, int tripIndexInBlock) {
			this.route = route;
			this.tripIndexInBlock = tripIndexInBlock;
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

		private final Trip trip;
		private final long startTime;
		private final long endTime;
		private final int tripStopIndex;
		private final String customDestination;

		private TripStopInfo(Trip trip, long startTime, long endTime, int tripStopIndex, String customDestination) {
			this.trip = trip;
			this.startTime = startTime;
			this.endTime = endTime;
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
