package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.booleans.BooleanLongImmutablePair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.*;
import org.mtr.core.Main;
import org.mtr.core.generated.SidingSchema;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public final class Siding extends SidingSchema implements Utilities {

	private PathData defaultPathData;
	private double timeOffsetForRepeating;
	private int departureSearchIndex;

	private final ObjectArrayList<SidingPathFinder<Depot, Siding, Station, Platform>> sidingPathFinderSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Depot, Siding>> sidingPathFinderMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRoute = new ObjectArrayList<>();

	private final ObjectArraySet<Vehicle> vehicles = new ObjectArraySet<>();
	private final ObjectImmutableList<ReaderBase> vehicleReaders;
	/**
	 * Trips this siding will serve
	 */
	private final ObjectArrayList<Trip> trips = new ObjectArrayList<>();
	/**
	 * Mapping of platform ID to stop times
	 */
	private final Long2ObjectAVLTreeMap<ObjectArraySet<Trip.StopTime>> platformTripStopTimes = new Long2ObjectAVLTreeMap<>();
	/**
	 * Absolute departures (millis after 12am UTC) for this siding only
	 */
	private final LongArrayList departures = new LongArrayList();
	private final LongArrayList tempReturnTimes = new LongArrayList();
	/**
	 * Current path speed changes, used for calculating duration along path
	 */
	private final ObjectArrayList<TimeSegment> timeSegments = new ObjectArrayList<>();
	/**
	 * Mapping of departure indices to real time vehicle times
	 */
	private final Long2LongAVLTreeMap vehicleTimesAlongRoute = new Long2LongAVLTreeMap();

	public static final double ACCELERATION_DEFAULT = 1D / 250000;
	public static final double MAX_ACCELERATION = 1D / 50000;
	public static final double MIN_ACCELERATION = 1D / 2500000;
	private static final String KEY_VEHICLES = "vehicles";

	public Siding(Position position1, Position position2, double railLength, TransportMode transportMode, Data data) {
		super(getRailLength(railLength), position1, position2, transportMode, data);
		vehicleReaders = ObjectImmutableList.of();
	}

	public Siding(ReaderBase readerBase, Data data) {
		super(DataFixer.convertSiding(readerBase), data);
		vehicleReaders = savePathDataReaderBase(readerBase, KEY_VEHICLES);
		updateData(readerBase);
		DataFixer.unpackSidingVehicleCars(readerBase, transportMode, railLength, vehicleCars);
		DataFixer.unpackSidingMaxVehicles(readerBase, value -> maxVehicles = value);
	}

	@Override
	public void serializeFullData(WriterBase writerBase) {
		super.serializeFullData(writerBase);
		writerBase.writeDataset(vehicles, KEY_VEHICLES);
	}

	public void init() {
		final Rail rail = Data.tryGet(data.positionToRailConnections, position1, position2);
		if (rail != null) {
			defaultPathData = new PathData(rail, id, 1, -1, 0, rail.getLength(), position1, position2);
		}

		initPath(pathSidingToMainRoute, data);
		initPath(pathMainRouteToSiding, data);
		generatePathDistancesAndTimeSegments();

		if (area != null && defaultPathData != null) {
			vehicleReaders.forEach(readerBase -> vehicles.add(new Vehicle(VehicleExtraData.create(railLength, vehicleCars, pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData, area.getRepeatInfinitely(), acceleration, getIsManual(), maxManualSpeed, manualToAutomaticTime), this, readerBase, data)));
		}
	}

	public double getRailLength() {
		return railLength;
	}

	public ObjectArrayList<VehicleCar> getVehicleCars() {
		return vehicleCars;
	}

	public boolean getIsManual() {
		return maxVehicles < 0;
	}

	public boolean getIsUnlimited() {
		return maxVehicles == 0;
	}

	public long getMaxVehicles() {
		return getIsManual() ? 1 : maxVehicles;
	}

	public int getTransportModeOrdinal() {
		return transportMode.ordinal();
	}

	public double getAcceleration() {
		return acceleration;
	}

	public void setVehicleCars(ObjectArrayList<VehicleCar> newVehicleCars) {
		vehicleCars.clear();
		double tempVehicleLength = 0;
		for (final VehicleCar vehicleCar : newVehicleCars) {
			if (tempVehicleLength + vehicleCar.getLength() > railLength) {
				break;
			}
			vehicleCars.add(vehicleCar);
			tempVehicleLength += vehicleCar.getLength();
			if (vehicleCars.size() >= transportMode.maxLength) {
				break;
			}
		}
	}

	public void setIsManual(boolean isManual) {
		maxVehicles = transportMode.continuousMovement ? 0 : isManual ? -1 : 1;
	}

	public void setUnlimitedVehicles(boolean unlimitedVehicles) {
		maxVehicles = transportMode.continuousMovement ? 0 : unlimitedVehicles ? 0 : 1;
	}

	public void setMaxVehicles(int newMaxVehicles) {
		maxVehicles = transportMode.continuousMovement ? 0 : Math.max(1, newMaxVehicles);
	}

	public void setAcceleration(double newAcceleration) {
		acceleration = transportMode.continuousMovement ? MAX_ACCELERATION : roundAcceleration(newAcceleration);
	}

	public void clearVehicles() {
		vehicles.clear();
	}

	public void generateRoute(Platform firstPlatform, Platform lastPlatform, int stopIndex, long cruisingAltitude) {
		vehicles.clear();
		pathSidingToMainRoute.clear();
		pathMainRouteToSiding.clear();
		sidingPathFinderSidingToMainRoute.clear();
		sidingPathFinderSidingToMainRoute.add(new SidingPathFinder<>(data, this, firstPlatform, -1));
		sidingPathFinderMainRouteToSiding.clear();
		if (lastPlatform != null) {
			sidingPathFinderMainRouteToSiding.add(new SidingPathFinder<>(data, lastPlatform, this, stopIndex));
		}
	}

	public void tick() {
		SidingPathFinder.findPathTick(pathSidingToMainRoute, sidingPathFinderSidingToMainRoute, this::finishGeneratingPath, () -> {
			Main.LOGGER.info(String.format("Path not found from %s siding %s to main route", getDepotName(), name));
			finishGeneratingPath();
		});
		SidingPathFinder.findPathTick(pathMainRouteToSiding, sidingPathFinderMainRouteToSiding, () -> {
			if (area != null) {
				if (SidingPathFinder.overlappingPaths(area.getPath(), pathMainRouteToSiding)) {
					pathMainRouteToSiding.remove(0);
				}
			}
			finishGeneratingPath();
		}, () -> {
			Main.LOGGER.info(String.format("Path not found from main route to %s siding %s", getDepotName(), name));
			finishGeneratingPath();
		});
	}

	public void initVehiclePositions(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		vehicles.forEach(vehicle -> vehicle.initVehiclePositions(vehiclePositions));
	}

	public void simulateTrain(long millisElapsed, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions) {
		vehicleTimesAlongRoute.clear();

		if (area == null) {
			vehicles.clear();
			pathMainRoute.clear();
			pathSidingToMainRoute.clear();
			pathMainRouteToSiding.clear();
			return;
		}

		int trainsAtDepot = 0;
		boolean spawnTrain = true;

		final ObjectArraySet<Vehicle> trainsToRemove = new ObjectArraySet<>();
		final LongArrayList visitedDepartureIndices = new LongArrayList();
		for (final Vehicle vehicle : vehicles) {
			vehicle.simulate(millisElapsed, vehiclePositions, vehicleTimesAlongRoute);

			if (vehicle.closeToDepot()) {
				spawnTrain = false;
			}

			if (vehicle.getIsOnRoute()) {
				if (!getIsUnlimited()) {
					final long departureIndex = vehicle.getDepartureIndex();
					if (departureIndex < 0 || departureIndex >= departures.size() || visitedDepartureIndices.contains(departureIndex)) {
						trainsToRemove.add(vehicle);
					} else {
						visitedDepartureIndices.add(departureIndex);
					}
				}
			} else {
				trainsAtDepot++;
				if (trainsAtDepot > 1) {
					trainsToRemove.add(vehicle);
				} else if (!pathSidingToMainRoute.isEmpty() && !getIsManual()) {
					final int departureIndex = matchDeparture();
					if (departureIndex >= 0 && departureIndex < departures.size()) {
						vehicle.startUp(departureIndex);
					}
				}
			}
		}

		if (defaultPathData != null && !vehicleCars.isEmpty() && spawnTrain && (getIsUnlimited() || vehicles.size() < getMaxVehicles())) {
			vehicles.add(new Vehicle(VehicleExtraData.create(railLength, vehicleCars, pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData, area.getRepeatInfinitely(), acceleration, getIsManual(), maxManualSpeed, manualToAutomaticTime), this, transportMode, data));
		}

		if (!trainsToRemove.isEmpty()) {
			trainsToRemove.forEach(vehicles::remove);
		}
	}

	public void startGeneratingDepartures() {
		departures.clear();
		tempReturnTimes.clear();
		for (int i = 0; i < maxVehicles; i++) {
			tempReturnTimes.add(0);
		}
	}

	public boolean addDeparture(long departure) {
		if (getIsManual()) {
			return false;
		} else if (getIsUnlimited()) {
			departures.add(departure);
			return true;
		}

		if (!timeSegments.isEmpty() && area != null) {
			final TimeSegment lastTimeSegment = Utilities.getElement(timeSegments, -1);
			for (int i = 0; i < tempReturnTimes.size(); i++) {
				if (departure >= tempReturnTimes.getLong(i)) {
					departures.add(departure);
					tempReturnTimes.set(i, area.getRepeatInfinitely() ? Integer.MAX_VALUE : departure + (int) Math.ceil(lastTimeSegment.startTime + lastTimeSegment.startSpeed / lastTimeSegment.acceleration));
					return true;
				}
			}
		}

		return false;
	}

	public double getTimeAlongRoute(double railProgress) {
		final int index = Utilities.getIndexFromConditionalList(timeSegments, railProgress);
		return index < 0 ? -1 : timeSegments.get(index).getTimeAlongRoute(railProgress);
	}

	public void getOBAArrivalsAndDeparturesElementsWithTripsUsed(long currentMillis, Platform platform, int millsBefore, int millisAfter, JsonArray arrivalsAndDeparturesArray, JsonArray tripsUsedArray) {
		if (area == null || departures.isEmpty()) {
			return;
		}

		final ObjectArraySet<Trip.StopTime> tripStopTimes = platformTripStopTimes.get(platform.getId());
		if (tripStopTimes == null) {
			return;
		}

		final long repeatInterval = getRepeatInterval(MILLIS_PER_DAY);
		final ObjectAVLTreeSet<String> addedTripIds = new ObjectAVLTreeSet<>();

		tripStopTimes.forEach(stopTime -> {
			for (int departureIndex = 0; departureIndex < departures.size(); departureIndex++) {
				final long departure = departures.getLong(departureIndex);
				long departureOffset = (currentMillis - (transportMode.continuousMovement ? 0 : millsBefore) - repeatInterval / 2 - stopTime.endTime - departure) / repeatInterval + 1;
				final ObjectObjectImmutablePair<JsonObject, BooleanLongImmutablePair> vehicleStatusWithDeviation = getOBATripStatusWithDeviation(currentMillis, stopTime, departureIndex, departureOffset, platform.getHexId(), platform.getHexId());
				final boolean predicted = vehicleStatusWithDeviation.right().leftBoolean();
				final long deviation = vehicleStatusWithDeviation.right().rightLong();
				final Trip trip = stopTime.trip;

				while (true) {
					final String tripId = trip.getTripId(departureIndex, departureOffset);
					final long scheduledArrivalTime;
					final long scheduledDepartureTime;

					if (transportMode.continuousMovement) {
						scheduledArrivalTime = currentMillis + Depot.CONTINUOUS_MOVEMENT_FREQUENCY;
						scheduledDepartureTime = currentMillis + Depot.CONTINUOUS_MOVEMENT_FREQUENCY;
					} else {
						final long offsetMillis = repeatInterval * departureOffset;
						scheduledArrivalTime = stopTime.startTime + offsetMillis + departure;
						scheduledDepartureTime = stopTime.endTime + offsetMillis + departure;
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
					arrivalAndDepartureObject.addProperty("departureEnabled", stopTime.tripStopIndex < trip.route.getRoutePlatforms().size() - 1);
					arrivalAndDepartureObject.addProperty("distanceFromStop", 0);
					arrivalAndDepartureObject.add("frequency", getOBAFrequencyElement(currentMillis));
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
					arrivalAndDepartureObject.addProperty("routeLongName", Utilities.formatName(trip.route.getName()));
					arrivalAndDepartureObject.addProperty("routeShortName", Utilities.formatName(trip.route.getRouteNumber()));
					arrivalAndDepartureObject.add("scheduledArrivalInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("scheduledArrivalTime", scheduledArrivalTime);
					arrivalAndDepartureObject.add("scheduledDepartureInterval", JsonNull.INSTANCE);
					arrivalAndDepartureObject.addProperty("scheduledDepartureTime", scheduledDepartureTime);
					arrivalAndDepartureObject.addProperty("serviceDate", 0);
					arrivalAndDepartureObject.add("situationIds", new JsonArray());
					arrivalAndDepartureObject.addProperty("status", "default");
					arrivalAndDepartureObject.addProperty("stopId", platform.getHexId());
					arrivalAndDepartureObject.addProperty("stopSequence", stopTime.tripStopIndex);
					arrivalAndDepartureObject.addProperty("totalStopsInTrip", trip.route.getRoutePlatforms().size());
					arrivalAndDepartureObject.addProperty("tripHeadsign", stopTime.customDestination);
					arrivalAndDepartureObject.addProperty("tripId", tripId);
					arrivalAndDepartureObject.add("tripStatus", vehicleStatusWithDeviation.left());
					arrivalAndDepartureObject.addProperty("vehicleId", vehicleCars.isEmpty() ? "" : vehicleCars.get(0).getVehicleId());
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
				Utilities.getElement(departures, departureIndex, 0L) + departureOffset * getRepeatInterval(MILLIS_PER_DAY),
				departureIndex,
				departureOffset,
				Utilities.getElement(trips, tripIndex + 1),
				Utilities.getElement(trips, tripIndex - 1),
				platformIdsUsed,
				tripsUsedArray
		);
	}

	public ObjectObjectImmutablePair<JsonObject, BooleanLongImmutablePair> getOBATripStatusWithDeviation(long currentMillis, Trip.StopTime stopTime, int departureIndex, long departureOffset, String closestStop, String nextStop) {
		final boolean predicted;
		final long deviation;

		if (transportMode.continuousMovement) {
			predicted = true;
			deviation = 0;
		} else {
			final long timeAlongRoute = vehicleTimesAlongRoute.getOrDefault(departureIndex, -1);
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
		tripStatusObject.add("frequency", getOBAFrequencyElement(currentMillis));
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
		tripStatusObject.addProperty("vehicleId", vehicleCars.isEmpty() ? "" : vehicleCars.get(0).getVehicleId());

		return new ObjectObjectImmutablePair<>(tripStatusObject, new BooleanLongImmutablePair(predicted, deviation));
	}

	public JsonElement getOBAFrequencyElement(long currentMillis) {
		if (transportMode.continuousMovement) {
			final JsonObject frequencyObject = new JsonObject();
			frequencyObject.addProperty("endTime", currentMillis + MILLIS_PER_DAY);
			frequencyObject.addProperty("exactTimes", 0);
			frequencyObject.addProperty("headway", Depot.CONTINUOUS_MOVEMENT_FREQUENCY / MILLIS_PER_SECOND);
			frequencyObject.addProperty("startTime", 0);
			return frequencyObject;
		} else {
			return JsonNull.INSTANCE;
		}
	}

	private String getDepotName() {
		return area == null ? "" : area.getName();
	}

	private int matchDeparture() {
		final long repeatInterval = getRepeatInterval(0);
		final long offset = departures.isEmpty() || repeatInterval == 0 ? 0 : (System.currentTimeMillis() - departures.getLong(0)) / repeatInterval * repeatInterval;

		for (int i = 0; i < departures.size(); i++) {
			if (departureSearchIndex >= departures.size()) {
				departureSearchIndex = 0;
			}

			final int match = data instanceof Simulator ? ((Simulator) data).matchMillis(departures.getLong(departureSearchIndex) + offset) : 0;

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

	private long getRepeatInterval(long defaultAmount) {
		if (area == null) {
			return defaultAmount;
		} else {
			return transportMode.continuousMovement ? Depot.CONTINUOUS_MOVEMENT_FREQUENCY : area.getRepeatInfinitely() ? Math.round(timeOffsetForRepeating) : data instanceof Simulator && !area.getUseRealTime() ? ((Simulator) data).millisPerGameDay : defaultAmount;
		}
	}

	/**
	 * Should only be called after a path is generated, whether successful or not.
	 */
	private void finishGeneratingPath() {
		if (sidingPathFinderSidingToMainRoute.isEmpty() && sidingPathFinderMainRouteToSiding.isEmpty()) {
			generatePathDistancesAndTimeSegments();
			if (area != null) {
				area.finishGeneratingPath(id);
			}
		}
	}

	/**
	 * After a path is set, generate the distance and time values.
	 * Should only be called during initialization and after a path is generated.
	 */
	private void generatePathDistancesAndTimeSegments() {
		vehicles.clear();
		pathMainRoute.clear();
		trips.clear();
		platformTripStopTimes.clear();
		timeSegments.clear();

		if (pathSidingToMainRoute.isEmpty() || area == null || area.getPath().isEmpty() || !area.getRepeatInfinitely() && pathMainRouteToSiding.isEmpty()) {
			pathSidingToMainRoute.clear();
			pathMainRouteToSiding.clear();
		} else {
			pathMainRoute.addAll(area.getPath());
			final boolean overlappingFromRepeating = SidingPathFinder.overlappingPaths(pathMainRoute, pathMainRoute);
			final double totalVehicleLength = getTotalVehicleLength(vehicleCars);

			if (SidingPathFinder.overlappingPaths(pathSidingToMainRoute, pathMainRoute)) {
				final PathData pathData = pathMainRoute.remove(0);
				if (area.getRepeatInfinitely() && !overlappingFromRepeating) {
					pathMainRoute.add(pathData);
				}
			} else {
				if (area.getRepeatInfinitely() && overlappingFromRepeating) {
					pathSidingToMainRoute.add(pathMainRoute.remove(0));
				}
			}

			SidingPathFinder.generatePathDataDistances(pathSidingToMainRoute, 0);
			SidingPathFinder.generatePathDataDistances(pathMainRoute, Utilities.getElement(pathSidingToMainRoute, -1).getEndDistance());
			SidingPathFinder.generatePathDataDistances(pathMainRouteToSiding, Utilities.getElement(pathMainRoute, -1).getEndDistance());

			final ObjectArrayList<PathData> path = new ObjectArrayList<>();
			path.addAll(pathSidingToMainRoute);
			path.addAll(pathMainRoute);

			final double totalDistance = Utilities.getElement(path, -1).getEndDistance();
			final DoubleArrayList stoppingDistances = new DoubleArrayList();
			for (final PathData pathData : path) {
				if (pathData.getDwellTime() > 0) {
					stoppingDistances.add(pathData.getEndDistance());
				}
			}

			final ObjectArrayList<RoutePlatformInfo> routePlatformInfoList = new ObjectArrayList<>();
			for (int i = 0; i < area.routes.size(); i++) {
				final Route route = area.routes.get(i);
				for (int j = 0; j < route.getRoutePlatforms().size(); j++) {
					routePlatformInfoList.add(new RoutePlatformInfo(route, i, route.getRoutePlatforms().get(j).platform.getId(), route.getDestination(j)));
				}
			}

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
				final double railSpeed = pathData.getRail().canAccelerate() ? pathData.getRail().speedLimitMetersPerMillisecond : Math.max(speed, transportMode.defaultSpeedMetersPerMillisecond);
				final double currentDistance = pathData.getEndDistance();

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

				if (pathData.getSavedRailBaseId() != 0) {
					final long startTime = Math.round(time);
					time += pathData.getDwellTime();
					final long endTime = Math.round(time);

					while (!routePlatformInfoList.isEmpty()) {
						final RoutePlatformInfo routePlatformInfo = routePlatformInfoList.get(0);

						if (routePlatformInfo.platformId != pathData.getSavedRailBaseId()) {
							break;
						}

						if (!platformTripStopTimes.containsKey(pathData.getSavedRailBaseId())) {
							platformTripStopTimes.put(pathData.getSavedRailBaseId(), new ObjectArraySet<>());
						}

						final Trip currentTrip = Utilities.getElement(trips, -1);
						if (currentTrip == null || routePlatformInfo.routeIndex != currentTrip.routeIndex) {
							final Trip trip = new Trip(routePlatformInfo.route, routePlatformInfo.routeIndex, trips.size(), this);
							tripStopIndex = 0;
							platformTripStopTimes.get(pathData.getSavedRailBaseId()).add(trip.addStopTime(startTime, endTime, pathData.getSavedRailBaseId(), 0, routePlatformInfo.customDestination));
							trips.add(trip);
						} else {
							platformTripStopTimes.get(pathData.getSavedRailBaseId()).add(currentTrip.addStopTime(startTime, endTime, pathData.getSavedRailBaseId(), tripStopIndex, routePlatformInfo.customDestination));
						}

						tripStopIndex++;
						routePlatformInfoList.remove(0);
					}
				} else {
					time += pathData.getDwellTime();
				}

				if (i + 1 < path.size() && pathData.isOppositeRail(path.get(i + 1))) {
					railProgress += totalVehicleLength;
				}
			}

			timeOffsetForRepeating = time - timeOffsetForRepeating;
		}
	}

	public static double getRailLength(double rawRailLength) {
		return Utilities.round(rawRailLength, 3);
	}

	public static ObjectImmutableList<ReaderBase> savePathDataReaderBase(ReaderBase readerBase, String key) {
		final ObjectArrayList<ReaderBase> tempReaders = new ObjectArrayList<>();
		readerBase.iterateReaderArray(key, tempReaders::add);
		return new ObjectImmutableList<>(tempReaders);
	}

	public static void initPath(ObjectArrayList<PathData> path, Data data) {
		final ObjectArrayList<PathData> pathDataToRemove = new ObjectArrayList<>();
		path.forEach(pathData -> {
			if (pathData.init(data)) {
				pathDataToRemove.add(pathData);
			}
		});
		pathDataToRemove.forEach(path::remove);
	}

	public static double getTotalVehicleLength(ObjectArrayList<VehicleCar> vehicleCars) {
		double totalVehicleLength = 0;
		for (final VehicleCar vehicleCar : vehicleCars) {
			totalVehicleLength += vehicleCar.getLength();
		}
		return totalVehicleLength;
	}

	public static double roundAcceleration(double acceleration) {
		final double tempAcceleration = Utilities.round(acceleration, 8);
		return tempAcceleration <= 0 ? ACCELERATION_DEFAULT : Utilities.clamp(tempAcceleration, MIN_ACCELERATION, MAX_ACCELERATION);
	}

	private static class RoutePlatformInfo {

		private final Route route;
		private final int routeIndex;
		private final long platformId;
		private final String customDestination;

		private RoutePlatformInfo(Route route, int routeIndex, long platformId, String customDestination) {
			this.route = route;
			this.routeIndex = routeIndex;
			this.platformId = platformId;
			this.customDestination = customDestination == null ? "" : Utilities.formatName(customDestination);
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
			this.acceleration = roundAcceleration(acceleration);
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
