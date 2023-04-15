package org.mtr.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.booleans.BooleanLongImmutablePair;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2LongAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.*;
import org.msgpack.core.MessagePacker;
import org.mtr.core.Main;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.reader.MessagePackHelper;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.Random;

public class Siding extends SavedRailBase<Siding, Depot> implements Utilities {

	private int maxVehicles;
	private double maxManualSpeed;
	private double acceleration = Train.ACCELERATION_DEFAULT;
	private PathData defaultPathData;
	private double timeOffsetForRepeating;
	private int departureSearchIndex;

	public final Simulator simulator;
	public final double railLength;

	private final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Depot, Siding, Station, Platform>> sidingPathFinderSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Depot, Siding>> sidingPathFinderMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArraySet<Train> vehicles = new ObjectArraySet<>();
	private final ObjectImmutableList<MessagePackHelper> vehicleMessagePackHelpers;
	private final ObjectArrayList<Trip> trips = new ObjectArrayList<>();
	private final Long2ObjectAVLTreeMap<ObjectArraySet<Trip.StopTime>> platformTripStopTimes = new Long2ObjectAVLTreeMap<>();
	private final IntArrayList departures = new IntArrayList();
	private final IntArrayList tempReturnTimes = new IntArrayList();
	private final ObjectArrayList<TimeSegment> timeSegments = new ObjectArrayList<>();
	private final Int2LongAVLTreeMap vehicleTimesAlongRoute = new Int2LongAVLTreeMap();

	private static final String KEY_RAIL_LENGTH = "rail_length";
	private static final String KEY_VEHICLE_CARS = "vehicle_cars";
	private static final String KEY_PATH_SIDING_TO_MAIN_ROUTE = "path_siding_to_main_route";
	private static final String KEY_PATH_MAIN_ROUTE_TO_SIDING = "path_main_route_to_siding";
	private static final String KEY_VEHICLES = "trains";
	private static final String KEY_MAX_VEHICLES = "max_vehicles";
	private static final String KEY_MAX_MANUAL_SPEED = "max_manual_speed_meters_per_millisecond";
	private static final String KEY_ACCELERATION = "acceleration";

	public Siding(Simulator simulator, long id, TransportMode transportMode, Position pos1, Position pos2, double railLength) {
		super(id, transportMode, pos1, pos2);

		this.simulator = simulator;
		this.railLength = getRailLength(railLength);
		acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.ACCELERATION_DEFAULT;
		vehicleMessagePackHelpers = ObjectImmutableList.of();
	}

	public <T extends ReaderBase<U, T>, U> Siding(T readerBase, Simulator simulator) {
		super(readerBase);

		this.simulator = simulator;
		railLength = getRailLength(readerBase.getDouble(KEY_RAIL_LENGTH, 0));
		readerBase.iterateReaderArray(KEY_PATH_SIDING_TO_MAIN_ROUTE, pathSection -> pathSidingToMainRoute.add(new PathData(pathSection)));
		readerBase.iterateReaderArray(KEY_PATH_MAIN_ROUTE_TO_SIDING, pathSection -> pathMainRouteToSiding.add(new PathData(pathSection)));
		final ObjectArrayList<MessagePackHelper> tempVehicleMessagePackHelpers = new ObjectArrayList<>();
		readerBase.iterateReaderArray(KEY_VEHICLES, vehicleReaderBase -> {
			if (vehicleReaderBase instanceof MessagePackHelper) {
				tempVehicleMessagePackHelpers.add((MessagePackHelper) vehicleReaderBase);
			}
		});
		vehicleMessagePackHelpers = new ObjectImmutableList<>(tempVehicleMessagePackHelpers);

		updateData(readerBase);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		super.updateData(readerBase);

		final ObjectArrayList<VehicleCar> tempVehicleCars = new ObjectArrayList<>();
		if ((readerBase instanceof MessagePackHelper) && readerBase.iterateReaderArray(KEY_VEHICLE_CARS, vehicleCar -> tempVehicleCars.add(new VehicleCar((MessagePackHelper) vehicleCar)))) {
			setVehicle(tempVehicleCars);
		}
		DataFixer.unpackVehicleCars(readerBase, transportMode, railLength, this::setVehicle);

		readerBase.unpackInt(KEY_MAX_VEHICLES, value -> maxVehicles = transportMode.continuousMovement ? 0 : value);
		DataFixer.unpackMaxVehicles(readerBase, value -> maxVehicles = transportMode.continuousMovement ? 0 : value);
		readerBase.unpackDouble(KEY_MAX_MANUAL_SPEED, value -> maxManualSpeed = value);
		DataFixer.unpackMaxManualSpeed(readerBase, value -> maxManualSpeed = value);
		readerBase.unpackDouble(KEY_ACCELERATION, value -> acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.roundAcceleration(value));
		DataFixer.unpackAcceleration(readerBase, value -> acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.roundAcceleration(value));
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_RAIL_LENGTH).packDouble(railLength);
		MessagePackHelper.writeMessagePackDataset(messagePacker, vehicleCars, KEY_VEHICLE_CARS);
		MessagePackHelper.writeMessagePackDataset(messagePacker, sidingPathFinderSidingToMainRoute.isEmpty() ? pathSidingToMainRoute : new ObjectArrayList<>(), KEY_PATH_SIDING_TO_MAIN_ROUTE);
		MessagePackHelper.writeMessagePackDataset(messagePacker, sidingPathFinderMainRouteToSiding.isEmpty() ? pathMainRouteToSiding : new ObjectArrayList<>(), KEY_PATH_MAIN_ROUTE_TO_SIDING);
		messagePacker.packString(KEY_MAX_VEHICLES).packInt(maxVehicles);
		messagePacker.packString(KEY_MAX_MANUAL_SPEED).packDouble(maxManualSpeed);
		messagePacker.packString(KEY_ACCELERATION).packDouble(acceleration);
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 7;
	}

	@Override
	public void toFullMessagePack(MessagePacker messagePacker) throws IOException {
		super.toFullMessagePack(messagePacker);
		MessagePackHelper.writeMessagePackDataset(messagePacker, vehicles, KEY_VEHICLES);
	}

	@Override
	public int fullMessagePackLength() {
		return super.fullMessagePackLength() + 1;
	}

	public void init() {
		final Position position1 = positions.left();
		final Position position2 = positions.right();
		final Rail rail = DataCache.tryGet(simulator.dataCache.positionToRailConnections, position1, position2);
		if (rail != null) {
			defaultPathData = new PathData(rail, id, 1, -1, 0, rail.getLength(), position1, position2);
		}

		generatePathDistancesAndTimeSegments();
		if (area != null && defaultPathData != null) {
			vehicleMessagePackHelpers.forEach(messagePackHelper -> vehicles.add(new Train(
					this, railLength, vehicleCars,
					pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData,
					area.repeatInfinitely, acceleration, maxVehicles < 0, maxManualSpeed, getTimeValueMillis(), messagePackHelper
			)));
		}
	}

	public void generateRoute(Platform firstPlatform, Platform lastPlatform, int stopIndex, int cruisingAltitude) {
		vehicles.clear();
		pathSidingToMainRoute.clear();
		pathMainRouteToSiding.clear();
		sidingPathFinderSidingToMainRoute.clear();
		sidingPathFinderSidingToMainRoute.add(new SidingPathFinder<>(simulator.dataCache, this, firstPlatform, -1));
		sidingPathFinderMainRouteToSiding.clear();
		if (lastPlatform != null) {
			sidingPathFinderMainRouteToSiding.add(new SidingPathFinder<>(simulator.dataCache, lastPlatform, this, stopIndex));
		}
	}

	public void tick(Object2LongAVLTreeMap<Position> vehiclePositions) {
		SidingPathFinder.findPathTick(pathSidingToMainRoute, sidingPathFinderSidingToMainRoute, this::finishGeneratingPath, () -> {
			Main.LOGGER.info(String.format("Path not found from %s siding %s to main route", getDepotName(), name));
			finishGeneratingPath();
		});
		SidingPathFinder.findPathTick(pathMainRouteToSiding, sidingPathFinderMainRouteToSiding, () -> {
			if (area != null) {
				if (SidingPathFinder.overlappingPaths(area.path, pathMainRouteToSiding)) {
					pathMainRouteToSiding.remove(0);
				}
			}
			finishGeneratingPath();
		}, () -> {
			Main.LOGGER.info(String.format("Path not found from main route to %s siding %s", getDepotName(), name));
			finishGeneratingPath();
		});

		vehicles.forEach(train -> train.writeVehiclePositionsAndTimes(vehiclePositions, vehicleTimesAlongRoute));
	}

	public void simulateTrain(long millisElapsed, Object2LongAVLTreeMap<Position> vehiclePositions) {
		if (area == null) {
			return;
		}

		final boolean isManual = maxVehicles < 0;
		int trainsAtDepot = 0;
		boolean spawnTrain = true;

		final ObjectArraySet<Train> trainsToRemove = new ObjectArraySet<>();
		final IntArrayList visitedDepartureIndices = new IntArrayList();
		for (final Train train : vehicles) {
			train.simulateTrain(millisElapsed, vehiclePositions);

			if (train.closeToDepot()) {
				spawnTrain = false;
			}

			if (train.getIsOnRoute()) {
				if (maxVehicles != 0) {
					final int departureIndex = train.getDepartureIndex();
					if (departureIndex < 0 || departureIndex >= departures.size() || visitedDepartureIndices.contains(departureIndex)) {
						trainsToRemove.add(train);
					} else {
						visitedDepartureIndices.add(departureIndex);
					}
				}
			} else {
				trainsAtDepot++;
				if (trainsAtDepot > 1) {
					trainsToRemove.add(train);
				} else if (!pathSidingToMainRoute.isEmpty() && !isManual) {
					final int departureIndex = matchDeparture();
					if (departureIndex >= 0 && departureIndex < departures.size()) {
						train.startUp(departureIndex);
					}
				}
			}
		}

		if (defaultPathData != null && !vehicleCars.isEmpty() && spawnTrain && (maxVehicles == 0 || vehicles.size() < (isManual ? 1 : maxVehicles))) {
			vehicles.add(new Train(
					new Random().nextLong(), this, transportMode, railLength, vehicleCars,
					pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData,
					area.repeatInfinitely, acceleration, isManual, maxManualSpeed, getTimeValueMillis()
			));
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

	public boolean addDeparture(int departure) {
		if (maxVehicles < 0) {
			return false;
		} else if (maxVehicles == 0) {
			departures.add(departure);
			return true;
		}

		if (!timeSegments.isEmpty() && area != null) {
			final TimeSegment lastTimeSegment = Utilities.getElement(timeSegments, -1);
			for (int i = 0; i < tempReturnTimes.size(); i++) {
				if (departure >= tempReturnTimes.getInt(i)) {
					departures.add(departure);
					tempReturnTimes.set(i, area.repeatInfinitely ? Integer.MAX_VALUE : departure + (int) Math.ceil(lastTimeSegment.startTime + lastTimeSegment.startSpeed / lastTimeSegment.acceleration));
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

		final ObjectArraySet<Trip.StopTime> tripStopTimes = platformTripStopTimes.get(platform.id);
		if (tripStopTimes == null) {
			return;
		}

		final long repeatInterval = getRepeatInterval(MILLIS_PER_DAY);
		final ObjectAVLTreeSet<String> addedTripIds = new ObjectAVLTreeSet<>();

		tripStopTimes.forEach(stopTime -> {
			for (int departureIndex = 0; departureIndex < departures.size(); departureIndex++) {
				final int departure = departures.getInt(departureIndex);
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
					arrivalAndDepartureObject.addProperty("departureEnabled", stopTime.tripStopIndex < trip.route.platformIds.size() - 1);
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
					arrivalAndDepartureObject.addProperty("routeLongName", Utilities.formatName(trip.route.name));
					arrivalAndDepartureObject.addProperty("routeShortName", Utilities.formatName(trip.route.routeNumber));
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
					arrivalAndDepartureObject.addProperty("vehicleId", vehicleCars.isEmpty() ? "" : vehicleCars.get(0).vehicleId);
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
				Utilities.getElement(departures, departureIndex, 0) + departureOffset * getRepeatInterval(MILLIS_PER_DAY),
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
		tripStatusObject.addProperty("vehicleId", vehicleCars.isEmpty() ? "" : vehicleCars.get(0).vehicleId);

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
		return area == null ? "" : area.name;
	}

	private int matchDeparture() {
		final long repeatInterval = getRepeatInterval(0);
		final long offset = departures.isEmpty() || repeatInterval == 0 ? 0 : (System.currentTimeMillis() - departures.getInt(0)) / repeatInterval * repeatInterval;

		for (int i = 0; i < departures.size(); i++) {
			if (departureSearchIndex >= departures.size()) {
				departureSearchIndex = 0;
			}

			final int match = simulator.matchMillis(departures.getInt(departureSearchIndex) + offset);

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

	private void setVehicle(ObjectArrayList<VehicleCar> newVehicleCars) {
		vehicleCars.clear();
		int tempVehicleLength = 0;
		for (final VehicleCar vehicleCar : newVehicleCars) {
			if (tempVehicleLength + vehicleCar.length > railLength) {
				break;
			}
			vehicleCars.add(vehicleCar);
			tempVehicleLength += vehicleCar.length;
			if (vehicleCars.size() >= transportMode.maxLength) {
				break;
			}
		}
	}

	private long getRepeatInterval(long defaultAmount) {
		if (area == null) {
			return defaultAmount;
		} else {
			return transportMode.continuousMovement ? Depot.CONTINUOUS_MOVEMENT_FREQUENCY : area.repeatInfinitely ? Math.round(timeOffsetForRepeating) : area.useRealTime ? defaultAmount : simulator.millisPerGameDay;
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
		timeSegments.clear();

		if (pathSidingToMainRoute.isEmpty() || area == null || area.path.isEmpty() || !area.repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			pathSidingToMainRoute.clear();
			pathMainRouteToSiding.clear();
		} else {
			pathMainRoute.addAll(area.path);
			final boolean overlappingFromRepeating = SidingPathFinder.overlappingPaths(pathMainRoute, pathMainRoute);
			final double totalVehicleLength = getTotalVehicleLength(vehicleCars);

			if (SidingPathFinder.overlappingPaths(pathSidingToMainRoute, pathMainRoute)) {
				final PathData pathData = pathMainRoute.remove(0);
				if (area.repeatInfinitely && !overlappingFromRepeating) {
					pathMainRoute.add(pathData);
				}
			} else {
				if (area.repeatInfinitely && overlappingFromRepeating) {
					pathSidingToMainRoute.add(pathMainRoute.remove(0));
				}
			}

			SidingPathFinder.generatePathDataDistances(pathSidingToMainRoute, 0);
			SidingPathFinder.generatePathDataDistances(pathMainRoute, Utilities.getElement(pathSidingToMainRoute, -1).endDistance);
			SidingPathFinder.generatePathDataDistances(pathMainRouteToSiding, Utilities.getElement(pathMainRoute, -1).endDistance);

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
			area.iterateRoutes((route, routeIndex) -> {
				for (int i = 0; i < route.platformIds.size(); i++) {
					routePlatformInfoList.add(new RoutePlatformInfo(route, routeIndex, route.platformIds.get(i).platformId, route.getDestination(simulator.dataCache, i)));
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
						if (currentTrip == null || routePlatformInfo.routeIndex != currentTrip.routeIndex) {
							final Trip trip = new Trip(routePlatformInfo.route, routePlatformInfo.routeIndex, trips.size(), this);
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
	}

	public static double getRailLength(double rawRailLength) {
		return Utilities.round(rawRailLength, 3);
	}

	public static double getTotalVehicleLength(ObjectArrayList<VehicleCar> vehicleCars) {
		double totalVehicleLength = 0;
		for (final VehicleCar vehicleCar : vehicleCars) {
			totalVehicleLength += vehicleCar.length;
		}
		return totalVehicleLength;
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
