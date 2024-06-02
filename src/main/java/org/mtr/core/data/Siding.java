package org.mtr.core.data;

import org.mtr.core.Main;
import org.mtr.core.generated.data.SidingSchema;
import org.mtr.core.oba.*;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.ConditionalList;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.DataFixer;
import org.mtr.libraries.it.unimi.dsi.fastutil.booleans.BooleanLongImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongObjectImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Random;

public final class Siding extends SidingSchema implements Utilities {

	private PathData defaultPathData;
	private double timeOffsetForRepeating;

	private final ObjectArrayList<SidingPathFinder<Depot, Siding, Station, Platform>> sidingPathFinderSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Depot, Siding>> sidingPathFinderMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRouteToSiding = new ObjectArrayList<>();
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
	private static final String KEY_PATH_SIDING_TO_MAIN_ROUTE = "pathSidingToMainRoute";
	private static final String KEY_PATH_MAIN_ROUTE_TO_SIDING = "pathMainRouteToSiding";
	private static final String KEY_VEHICLES = "vehicles";

	public Siding(Position position1, Position position2, double railLength, TransportMode transportMode, Data data) {
		super(getRailLength(railLength), position1, position2, transportMode, data);
		vehicleReaders = ObjectImmutableList.of();
	}

	public Siding(ReaderBase readerBase, Data data) {
		super(DataFixer.convertSiding(readerBase), data);
		readerBase.iterateReaderArray(KEY_PATH_SIDING_TO_MAIN_ROUTE, pathSidingToMainRoute::clear, readerBaseChild -> pathSidingToMainRoute.add(new PathData(readerBaseChild)));
		readerBase.iterateReaderArray(KEY_PATH_MAIN_ROUTE_TO_SIDING, pathMainRouteToSiding::clear, readerBaseChild -> pathMainRouteToSiding.add(new PathData(readerBaseChild)));
		vehicleReaders = savePathDataReaderBase(readerBase, KEY_VEHICLES);
		updateData(readerBase);
		DataFixer.unpackSidingVehicleCars(readerBase, transportMode, railLength, vehicleCars);
		DataFixer.unpackSidingMaxVehicles(readerBase, value -> maxVehicles = value);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);
		vehicles.removeIf(vehicle -> !vehicle.getIsOnRoute());
	}

	@Override
	public void serializeFullData(WriterBase writerBase) {
		super.serializeFullData(writerBase);
		writerBase.writeDataset(pathSidingToMainRoute, KEY_PATH_SIDING_TO_MAIN_ROUTE);
		writerBase.writeDataset(pathMainRouteToSiding, KEY_PATH_MAIN_ROUTE_TO_SIDING);
		writerBase.writeDataset(vehicles, KEY_VEHICLES);
	}

	/**
	 * Should only be called during initialization and when a siding is created (by building a siding rail)
	 */
	public void init() {
		tick();
		generatePathDistancesAndTimeSegments();
		if (area != null && defaultPathData != null) {
			vehicleReaders.forEach(readerBase -> vehicles.add(new Vehicle(VehicleExtraData.create(id, railLength, vehicleCars, pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData, area.getRepeatInfinitely(), acceleration, deceleration, getIsManual(), maxManualSpeed, manualToAutomaticTime), this, readerBase, data)));
		}
		// Automatically clamp acceleration and deceleration values
		setAcceleration(acceleration);
		setDeceleration(deceleration);
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

	public double getDeceleration() {
		return deceleration;
	}

	public void setVehicleCars(ObjectArrayList<VehicleCar> newVehicleCars) {
		vehicleCars.clear();
		double tempVehicleLength = 0;
		for (int i = 0; i < newVehicleCars.size(); i++) {
			final VehicleCar vehicleCar = newVehicleCars.get(i);
			if (tempVehicleLength + vehicleCar.getTotalLength(i == 0, true) > railLength) {
				break;
			}
			vehicleCars.add(vehicleCar);
			tempVehicleLength += vehicleCar.getTotalLength(i == 0, false);
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

	public void setDeceleration(double newDeceleration) {
		deceleration = transportMode.continuousMovement ? MAX_ACCELERATION : roundAcceleration(newDeceleration);
	}

	public void clearVehicles() {
		vehicles.clear();
	}

	public void generateRoute(Platform firstPlatform, @Nullable Platform lastPlatform, int stopIndex, long cruisingAltitude) {
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

	public boolean tick() {
		// Generate any pending paths
		SidingPathFinder.findPathTick(pathSidingToMainRoute, sidingPathFinderSidingToMainRoute, area == null ? 0 : area.getCruisingAltitude(), () -> finishGeneratingPath(false), (startSavedRail, endSavedRail) -> {
			Main.LOGGER.info("Path not found from {} siding {} to main route", getDepotName(), name);
			finishGeneratingPath(true);
		});
		SidingPathFinder.findPathTick(pathMainRouteToSiding, sidingPathFinderMainRouteToSiding, area == null ? 0 : area.getCruisingAltitude(), () -> {
			if (area != null) {
				if (SidingPathFinder.overlappingPaths(area.getPath(), pathMainRouteToSiding)) {
					pathMainRouteToSiding.remove(0);
				}
			}
			finishGeneratingPath(false);
		}, (startSavedRail, endSavedRail) -> {
			Main.LOGGER.info("Path not found from main route to {} siding {}", getDepotName(), name);
			finishGeneratingPath(true);
		});

		// Attempt to find a corresponding rail for this siding and return true if failed
		if (defaultPathData == null) {
			final Rail rail = Data.tryGet(data.positionsToRail, position1, position2);
			if (rail != null) {
				defaultPathData = new PathData(rail, id, 1, -1, 0, rail.railMath.getLength(), position1, rail.getStartAngle(position1), position2, rail.getStartAngle(position2));
			}
			return defaultPathData == null;
		} else {
			return false;
		}
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
						if (!transportMode.continuousMovement && vehicles.stream().anyMatch(checkVehicle -> checkVehicle.getDepartureIndex() == departureIndex)) {
							Main.LOGGER.debug("Already deployed vehicle from {} for departure index {}", getDepotName(), departureIndex);
						} else {
							vehicle.startUp(departureIndex);
						}
					}
				}
			}
		}

		if (defaultPathData != null && !vehicleCars.isEmpty() && spawnTrain && (getIsUnlimited() || vehicles.size() < getMaxVehicles())) {
			vehicles.add(new Vehicle(VehicleExtraData.create(id, railLength, vehicleCars, pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData, area.getRepeatInfinitely(), acceleration, deceleration, getIsManual(), maxManualSpeed, manualToAutomaticTime), this, transportMode, data));
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
			final long journeyTime = getJourneyTime();
			for (int i = 0; i < tempReturnTimes.size(); i++) {
				if (departure >= tempReturnTimes.getLong(i)) {
					departures.add(departure);
					tempReturnTimes.set(i, area.getRepeatInfinitely() ? Long.MAX_VALUE : departure + journeyTime);
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

	public void updateVehicleRidingEntities(long vehicleId, ObjectArrayList<VehicleRidingEntity> vehicleRidingEntities) {
		for (final Vehicle vehicle : vehicles) {
			if (vehicle.getId() == vehicleId) {
				vehicle.updateRidingEntities(vehicleRidingEntities);
				break;
			}
		}
	}

	public void getArrivals(long currentMillis, Platform platform, long count, ObjectArrayList<ArrivalResponse> arrivalResponseList) {
		final long[] maxArrivalAndCount = {0, 0};
		final ObjectArrayList<ArrivalResponse> tempArrivalResponseList = new ObjectArrayList<>();

		iterateArrivals(currentMillis, platform.getId(), 0, MILLIS_PER_DAY, (trip, tripStopIndex, stopTime, scheduledArrivalTime, scheduledDepartureTime, predicted, deviation, departureIndex, departureOffset) -> {
			if (scheduledArrivalTime + deviation < maxArrivalAndCount[0] || maxArrivalAndCount[1] < count) {
				final ArrivalResponse arrivalResponse = new ArrivalResponse(stopTime.customDestination, scheduledArrivalTime + deviation, scheduledDepartureTime + deviation, deviation, predicted, departureIndex, stopTime.tripStopIndex, trip.route, platform);
				arrivalResponse.setCarDetails(getVehicleCars());
				tempArrivalResponseList.add(arrivalResponse);
				maxArrivalAndCount[0] = Math.max(maxArrivalAndCount[0], scheduledArrivalTime + deviation);
				maxArrivalAndCount[1]++;
			}
		});

		Collections.sort(tempArrivalResponseList);
		for (int i = 0; i < Math.min(tempArrivalResponseList.size(), count); i++) {
			arrivalResponseList.add(tempArrivalResponseList.get(i));
		}
	}

	public void getArrivals(long startMillis, long platformId, ArrivalPathFindingConsumer consumer) {
		if (area != null) {
			final Long2ObjectAVLTreeMap<LongObjectImmutablePair<Runnable>> stopTimesForPlatform = new Long2ObjectAVLTreeMap<>();
			iterateArrivals(startMillis, platformId, 0, MILLIS_PER_DAY, (trip, tripStopIndex, stopTime, scheduledArrivalTime, scheduledDepartureTime, predicted, deviation, departureIndex, departureOffset) -> trip.getUpcomingStopTimes(
					tripStopIndex,
					trips,
					area.getRepeatInfinitely(),
					newStopTime -> {
						final long departureTime = scheduledDepartureTime + deviation;
						final LongObjectImmutablePair<Runnable> existingStopTime = stopTimesForPlatform.get(newStopTime.platformId);
						if (existingStopTime == null || departureTime < existingStopTime.leftLong()) {
							stopTimesForPlatform.put(newStopTime.platformId, new LongObjectImmutablePair<>(departureTime, () -> consumer.accept(newStopTime.platformId, newStopTime.trip.route.getId(), departureTime, newStopTime.startTime - stopTime.endTime)));
						}
					}
			));
			stopTimesForPlatform.values().forEach(departureTimePair -> departureTimePair.right().run());
		}
	}

	public void getOBAArrivalsAndDeparturesElementsWithTripsUsed(SingleElement<StopWithArrivalsAndDepartures> singleElement, StopWithArrivalsAndDepartures stopWithArrivalsAndDepartures, long currentMillis, Platform platform, int millsBefore, int millisAfter) {
		final ObjectAVLTreeSet<String> addedTripIds = new ObjectAVLTreeSet<>();
		iterateArrivals(currentMillis, platform.getId(), millsBefore, millisAfter, (trip, tripStopIndex, stopTime, scheduledArrivalTime, scheduledDepartureTime, predicted, deviation, departureIndex, departureOffset) -> {
			final String tripId = trip.getTripId(departureIndex, departureOffset);
			stopWithArrivalsAndDepartures.add(ArrivalAndDeparture.create(
					trip,
					tripId,
					platform,
					stopTime,
					transportMode.continuousMovement ? currentMillis + Depot.CONTINUOUS_MOVEMENT_FREQUENCY : scheduledArrivalTime,
					transportMode.continuousMovement ? currentMillis + Depot.CONTINUOUS_MOVEMENT_FREQUENCY : scheduledDepartureTime,
					predicted,
					deviation,
					getOBAOccupancyStatus(predicted),
					getOBAVehicleId(departureIndex),
					getOBAFrequencyElement(currentMillis),
					new TripStatus(
							tripId,
							stopTime,
							"",
							"",
							getOBAOccupancyStatus(predicted),
							predicted,
							currentMillis,
							deviation,
							getOBAVehicleId(departureIndex),
							getOBAFrequencyElement(currentMillis)
					)
			));
			if (!addedTripIds.contains(tripId)) {
				singleElement.addTrip(trip.getOBATripElement(tripId, departureIndex));
				addedTripIds.add(tripId);
			}
		});
		stopWithArrivalsAndDepartures.sort();
	}

	public void getOBATripDetailsWithDataUsed(SingleElement<TripDetails> singleElement, long currentMillis, int tripIndex, int departureIndex, long departureOffset) {
		final Trip trip = Utilities.getElement(trips, tripIndex);
		if (trip != null) {
			trip.getOBATripDetailsWithDataUsed(
					singleElement,
					currentMillis,
					Utilities.getElement(departures, departureIndex, 0L) + departureOffset * getRepeatInterval(MILLIS_PER_DAY),
					departureIndex,
					departureOffset,
					Utilities.getElement(trips, tripIndex + 1),
					Utilities.getElement(trips, tripIndex - 1)
			);
		}
	}

	public TripStatus getOBATripStatus(long currentMillis, Trip.StopTime stopTime, int departureIndex, long departureOffset, String closestStop, String nextStop) {
		final BooleanLongImmutablePair predictedAndDeviation = getPredictedAndDeviation(currentMillis, departureIndex, departureOffset);
		final boolean predicted = predictedAndDeviation.leftBoolean();
		final long deviation = predictedAndDeviation.rightLong();
		return new TripStatus(
				stopTime.trip.getTripId(departureIndex, departureOffset),
				stopTime,
				closestStop,
				nextStop,
				getOBAOccupancyStatus(predicted),
				predicted,
				currentMillis,
				deviation,
				getOBAVehicleId(departureIndex),
				getOBAFrequencyElement(currentMillis)
		);
	}

	@Nullable
	public Frequency getOBAFrequencyElement(long currentMillis) {
		return transportMode.continuousMovement ? new Frequency(currentMillis) : null;
	}

	long getJourneyTime() {
		final TimeSegment lastTimeSegment = Utilities.getElement(timeSegments, -1);
		return lastTimeSegment == null ? 0 : (long) Math.ceil(lastTimeSegment.startTime + lastTimeSegment.startSpeed / lastTimeSegment.acceleration);
	}

	void writePathCache() {
		PathData.writePathCache(pathSidingToMainRoute, data, transportMode);
		PathData.writePathCache(pathMainRouteToSiding, data, transportMode);
	}

	private String getDepotName() {
		return area == null ? "" : area.getName();
	}

	private int matchDeparture() {
		final long repeatInterval = getRepeatInterval(0);
		final long offset = departures.isEmpty() || repeatInterval == 0 ? 0 : (System.currentTimeMillis() - departures.getLong(0)) / repeatInterval * repeatInterval;

		for (int i = 0; i < departures.size(); i++) {
			if ((data instanceof Simulator ? ((Simulator) data).matchMillis(departures.getLong(i) + offset) : 0) == 0) {
				return i;
			}
		}

		return -1;
	}

	private long getRepeatInterval(long defaultAmount) {
		if (area == null) {
			return defaultAmount;
		} else if (transportMode.continuousMovement) {
			return (long) Depot.CONTINUOUS_MOVEMENT_FREQUENCY * area.savedRails.size();
		} else if (area.getRepeatInfinitely()) {
			return Math.round(timeOffsetForRepeating);
		} else if (data instanceof Simulator && !area.getUseRealTime()) {
			return ((Simulator) data).getGameMillisPerDay() * area.getRepeatDepartures();
		} else {
			return defaultAmount;
		}
	}

	/**
	 * Should only be called after a path is generated, whether successful or not.
	 */
	private void finishGeneratingPath(boolean failed) {
		if (failed && area != null) {
			area.sidingPathGenerationFailed();
		}
		if (sidingPathFinderSidingToMainRoute.isEmpty() && sidingPathFinderMainRouteToSiding.isEmpty()) {
			generatePathDistancesAndTimeSegments();
			if (area != null) {
				area.finishGeneratingPath(id);
			}
		}
	}

	private BooleanLongImmutablePair getPredictedAndDeviation(long currentMillis, int departureIndex, long departureOffset) {
		final boolean predicted;
		final long deviation;

		if (transportMode.continuousMovement) {
			predicted = true;
			deviation = 0;
		} else {
			final long timeAlongRoute = vehicleTimesAlongRoute.getOrDefault(departureIndex, -1);
			predicted = timeAlongRoute >= 0;
			deviation = predicted ? Utilities.circularDifference(currentMillis - getRepeatInterval(MILLIS_PER_DAY) * departureOffset - departures.getLong(departureIndex), timeAlongRoute, getRepeatInterval(MILLIS_PER_DAY)) : 0;
		}

		return new BooleanLongImmutablePair(predicted, deviation);
	}

	private void iterateArrivals(long currentMillis, long platformId, long millsBefore, long millisAfter, ArrivalConsumer arrivalConsumer) {
		if (area == null || departures.isEmpty()) {
			return;
		}

		final ObjectArraySet<Trip.StopTime> tripStopTimes = platformTripStopTimes.get(platformId);
		if (tripStopTimes == null) {
			return;
		}

		final long repeatInterval = getRepeatInterval(MILLIS_PER_DAY);

		tripStopTimes.forEach(stopTime -> {
			for (int departureIndex = 0; departureIndex < departures.size(); departureIndex++) {
				final long departure = departures.getLong(departureIndex);
				long departureOffset = (currentMillis - (transportMode.continuousMovement ? 0 : millsBefore) - repeatInterval / 2 - stopTime.endTime - departure) / repeatInterval + 1;
				final BooleanLongImmutablePair predictedAndDeviation = getPredictedAndDeviation(currentMillis, departureIndex, departureOffset);
				final boolean predicted = predictedAndDeviation.leftBoolean();
				final long deviation = predictedAndDeviation.rightLong();
				final Trip trip = stopTime.trip;

				while (true) {
					final long scheduledArrivalTime;
					final long scheduledDepartureTime;

					if (transportMode.continuousMovement) {
						scheduledArrivalTime = 0;
						scheduledDepartureTime = 0;
					} else {
						final long offsetMillis = repeatInterval * departureOffset;
						scheduledArrivalTime = stopTime.startTime + offsetMillis + departure;
						scheduledDepartureTime = stopTime.endTime + offsetMillis + departure;
					}

					departureOffset++;

					if (scheduledArrivalTime > currentMillis + millisAfter + repeatInterval / 2) {
						break;
					} else if (!transportMode.continuousMovement) {
						final boolean outOfRange = scheduledDepartureTime + deviation < currentMillis - millsBefore || scheduledArrivalTime + deviation > currentMillis + millisAfter;
						final boolean missedDeparture = !predicted && scheduledArrivalTime - stopTime.startTime + MILLIS_PER_SECOND < currentMillis;
						if (outOfRange || missedDeparture) {
							continue;
						}
					}

					arrivalConsumer.accept(trip, stopTime.tripStopIndex, stopTime, scheduledArrivalTime, scheduledDepartureTime, predicted, deviation, departureIndex, departureOffset - 1);

					if (transportMode.continuousMovement) {
						break;
					}
				}
			}
		});
	}

	private OccupancyStatus getOBAOccupancyStatus(boolean predicted) {
		// TODO
		return predicted ? OccupancyStatus.values()[new Random().nextInt(OccupancyStatus.values().length - 2)] : OccupancyStatus.NO_DATA_AVAILABLE;
	}

	private String getOBAVehicleId(int departureIndex) {
		final ObjectArrayList<String> vehicleIds = new ObjectArrayList<>();
		vehicleCars.forEach(vehicleCar -> {
			final String vehicleId = vehicleCar.getVehicleId();
			final int index = vehicleId.lastIndexOf("_");
			final String trimmedVehicleId = index < 0 ? vehicleId : vehicleId.substring(0, index);
			if (!vehicleIds.contains(trimmedVehicleId)) {
				vehicleIds.add(trimmedVehicleId);
			}
		});
		return vehicleIds.isEmpty() ? "" : String.format("%s_%s", String.join("_", vehicleIds), departureIndex);
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
			path.addAll(pathMainRouteToSiding);

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
					final long platformId = route.getRoutePlatforms().get(j).platform.getId();
					if (j == 0 && !routePlatformInfoList.isEmpty() && Utilities.getElement(routePlatformInfoList, -1).platformId == platformId) {
						routePlatformInfoList.remove(routePlatformInfoList.size() - 1);
					}
					routePlatformInfoList.add(new RoutePlatformInfo(route, i, platformId, route.getDestination(j)));
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
				final double railSpeed = pathData.getRail().canAccelerate() ? pathData.getSpeedLimitMetersPerMillisecond() : Math.max(speed, transportMode.defaultSpeedMetersPerMillisecond);
				final double currentDistance = pathData.getEndDistance();

				while (railProgress < currentDistance) {
					final int speedChange;
					if (speed > railSpeed || nextStoppingDistance - railProgress + 1 < 0.5 * speed * speed / deceleration) {
						speed = Math.max(speed - deceleration, deceleration);
						speedChange = -1;
					} else if (speed < railSpeed) {
						speed = Math.min(speed + acceleration, railSpeed);
						speedChange = 1;
					} else {
						speedChange = 0;
					}

					if (timeSegments.isEmpty() || Utilities.getElement(timeSegments, -1).speedChange != speedChange) {
						timeSegments.add(new TimeSegment(railProgress, speed, time, speedChange, acceleration, deceleration));
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
		readerBase.iterateReaderArray(key, tempReaders::clear, tempReaders::add);
		return new ObjectImmutableList<>(tempReaders);
	}

	public static double getTotalVehicleLength(ObjectArrayList<VehicleCar> vehicleCars) {
		double totalVehicleLength = 0;
		for (int i = 0; i < vehicleCars.size(); i++) {
			totalVehicleLength += vehicleCars.get(i).getTotalLength(i == 0, i == vehicleCars.size() - 1);
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

		private RoutePlatformInfo(Route route, int routeIndex, long platformId, @Nullable String customDestination) {
			this.route = route;
			this.routeIndex = routeIndex;
			this.platformId = platformId;
			this.customDestination = customDestination == null ? "" : customDestination;
		}
	}

	private static class TimeSegment implements ConditionalList {

		private final double startRailProgress;
		private final double startSpeed;
		private final double startTime;
		private final int speedChange;
		private final double acceleration;
		private final double deceleration;

		private TimeSegment(double startRailProgress, double startSpeed, double startTime, int speedChange, double acceleration, double deceleration) {
			this.startRailProgress = startRailProgress;
			this.startSpeed = startSpeed;
			this.startTime = startTime;
			this.speedChange = speedChange;
			this.acceleration = roundAcceleration(acceleration);
			this.deceleration = roundAcceleration(deceleration);
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
				final double totalAcceleration = speedChange * (speedChange > 0 ? acceleration : deceleration);
				final double endSpeedSquared = 2 * totalAcceleration * distance + startSpeed * startSpeed;
				return endSpeedSquared < 0 ? -1 : startTime + (distance == 0 ? 0 : (Math.sqrt(endSpeedSquared) - startSpeed) / totalAcceleration);
			}
		}
	}

	@FunctionalInterface
	public interface ArrivalPathFindingConsumer {
		void accept(long platformId, long routeId, long departureTime, long duration);
	}

	@FunctionalInterface
	private interface ArrivalConsumer {
		void accept(Trip trip, int tripStopIndex, Trip.StopTime stopTime, long scheduledArrivalTime, long scheduledDepartureTime, boolean predicted, long deviation, int departureIndex, long departureOffset);
	}
}
