package org.mtr.core.data;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
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
import java.util.*;

public class Siding extends SavedRailBase<Siding, Depot> {

	private double totalVehicleLength;
	private boolean unlimitedVehicles;
	private int maxVehicles;
	private boolean isManual;
	private double maxManualSpeed;
	private double acceleration = Train.ACCELERATION_DEFAULT;

	public final Simulator simulator;
	public final double railLength;
	private final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Depot, Siding, Station, Platform>> sidingPathFinderSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Depot, Siding>> sidingPathFinderMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRouteToSiding = new ObjectArrayList<>();
	private final DoubleArrayList distances = new DoubleArrayList();
	private final ObjectArrayList<TimeSegment> timeSegments = new ObjectArrayList<>();
	private final Map<Long, Map<Long, Double>> platformTimes = new HashMap<>();
	private final Set<Train> vehicles = new HashSet<>();
	private final ObjectImmutableList<MessagePackHelper> vehicleMessagePackHelpers;

	private static final String KEY_RAIL_LENGTH = "rail_length";
	private static final String KEY_VEHICLE_CARS = "vehicle_cars";
	private static final String KEY_PATH_SIDING_TO_MAIN_ROUTE = "path_siding_to_main_route";
	private static final String KEY_PATH_MAIN_ROUTE_TO_SIDING = "path_main_route_to_siding";
	private static final String KEY_VEHICLES = "trains";
	private static final String KEY_UNLIMITED_VEHICLES = "unlimited_trains";
	private static final String KEY_MAX_VEHICLES = "max_trains";
	private static final String KEY_IS_MANUAL = "is_manual";
	private static final String KEY_MAX_MANUAL_SPEED = "max_manual_speed_meters_per_millisecond";
	private static final String KEY_ACCELERATION = "acceleration";

	public Siding(Simulator simulator, long id, TransportMode transportMode, Position pos1, Position pos2, double railLength) {
		super(id, transportMode, pos1, pos2);

		this.simulator = simulator;
		this.railLength = getRailLength(railLength);
		unlimitedVehicles = transportMode.continuousMovement;
		acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.ACCELERATION_DEFAULT;
		vehicleMessagePackHelpers = ObjectImmutableList.of();
	}

	public <T extends ReaderBase<U, T>, U> Siding(T readerBase, Simulator simulator) {
		super(readerBase);

		this.simulator = simulator;
		railLength = getRailLength(readerBase.getDouble(KEY_RAIL_LENGTH, 0));
		readerBase.iterateReaderArray(KEY_PATH_SIDING_TO_MAIN_ROUTE, pathSection -> pathSidingToMainRoute.add(new PathData(pathSection)));
		readerBase.iterateReaderArray(KEY_PATH_MAIN_ROUTE_TO_SIDING, pathSection -> pathMainRouteToSiding.add(new PathData(pathSection)));
		final List<MessagePackHelper> tempVehicleMessagePackHelpers = new ArrayList<>();
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

		readerBase.unpackBoolean(KEY_UNLIMITED_VEHICLES, value -> unlimitedVehicles = transportMode.continuousMovement || value);
		readerBase.unpackInt(KEY_MAX_VEHICLES, value -> maxVehicles = value);
		readerBase.unpackBoolean(KEY_IS_MANUAL, value -> isManual = value);
		readerBase.unpackDouble(KEY_MAX_MANUAL_SPEED, value -> maxManualSpeed = value);
		DataFixer.unpackMaxManualSpeed(readerBase, value -> maxManualSpeed = value);
		readerBase.unpackDouble(KEY_ACCELERATION, value -> acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.roundAcceleration(value));
		DataFixer.unpackAcceleration(readerBase, value -> acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.roundAcceleration(value));
	}

	@Override
	public void init() {
		vehicleMessagePackHelpers.forEach(messagePackHelper -> vehicles.add(new Train(
				id, railLength, vehicleCars, totalVehicleLength,
				pathSidingToMainRoute, getPathMainRoute(), pathMainRouteToSiding, distances,
				acceleration, isManual, maxManualSpeed, getTimeValueMillis(), messagePackHelper
		)));
		generateDistances();
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_RAIL_LENGTH).packDouble(railLength);
		MessagePackHelper.writeMessagePackDataset(messagePacker, vehicleCars, KEY_VEHICLE_CARS);
		MessagePackHelper.writeMessagePackDataset(messagePacker, pathSidingToMainRoute, KEY_PATH_SIDING_TO_MAIN_ROUTE);
		MessagePackHelper.writeMessagePackDataset(messagePacker, pathMainRouteToSiding, KEY_PATH_MAIN_ROUTE_TO_SIDING);
		messagePacker.packString(KEY_UNLIMITED_VEHICLES).packBoolean(unlimitedVehicles);
		messagePacker.packString(KEY_MAX_VEHICLES).packInt(maxVehicles);
		messagePacker.packString(KEY_IS_MANUAL).packBoolean(isManual);
		messagePacker.packString(KEY_MAX_MANUAL_SPEED).packDouble(maxManualSpeed);
		messagePacker.packString(KEY_ACCELERATION).packDouble(acceleration);
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 9;
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

	public double getAcceleration() {
		return acceleration;
	}

	public void generateRoute(Platform firstPlatform, Platform lastPlatform, int cruisingAltitude) {
		if (area == null) {
			vehicles.clear();
		}

		pathSidingToMainRoute.clear();
		pathMainRouteToSiding.clear();
		sidingPathFinderSidingToMainRoute.clear();
		sidingPathFinderSidingToMainRoute.add(new SidingPathFinder<>(simulator.dataCache, this, firstPlatform, -1));
		sidingPathFinderMainRouteToSiding.clear();
		if (lastPlatform != null) {
			sidingPathFinderMainRouteToSiding.add(new SidingPathFinder<>(simulator.dataCache, lastPlatform, this, -1));
		}
	}

	public void tick() {
		SidingPathFinder.findPathTick(simulator.dataCache, pathSidingToMainRoute, sidingPathFinderSidingToMainRoute, () -> {
			// start at this siding fully
			SidingPathFinder.addPathData(simulator.dataCache, pathSidingToMainRoute, true, this, pathSidingToMainRoute, false, -1);
			finishGeneratingPath();
		}, () -> {
			Main.LOGGER.info(String.format("Path not found from %s siding %s to main route", getDepotName(), name));
			finishGeneratingPath();
		});
		SidingPathFinder.findPathTick(simulator.dataCache, pathMainRouteToSiding, sidingPathFinderMainRouteToSiding, this::finishGeneratingPath, () -> {
			Main.LOGGER.info(String.format("Path not found from main route to %s siding %s", getDepotName(), name));
			finishGeneratingPath();
		});
	}

	public void simulateTrain(long millisElapsed) {
		if (area == null) {
			return;
		}

		int trainsAtDepot = 0;
		boolean spawnTrain = true;

		final Set<Long> railProgressSet = new HashSet<>();
		final Set<Train> trainsToRemove = new HashSet<>();
		for (final Train train : vehicles) {
			train.simulateTrain(millisElapsed, area);

			if (train.closeToDepot()) {
				spawnTrain = false;
			}

			if (!train.getIsOnRoute()) {
				trainsAtDepot++;
				if (trainsAtDepot > 1) {
					trainsToRemove.add(train);
				}
			}

			final long roundedRailProgress = Math.round(train.getRailProgress() * 10);
			if (railProgressSet.contains(roundedRailProgress)) {
				trainsToRemove.add(train);
			}
			railProgressSet.add(roundedRailProgress);
		}

		if (totalVehicleLength > 0 && (vehicles.isEmpty() || spawnTrain && (unlimitedVehicles || vehicles.size() <= maxVehicles))) {
			final Train train = new Train(
					unlimitedVehicles || maxVehicles > 0 ? new Random().nextLong() : id, id, transportMode, railLength, vehicleCars, totalVehicleLength,
					pathSidingToMainRoute, getPathMainRoute(), pathMainRouteToSiding, distances,
					acceleration, isManual, maxManualSpeed, getTimeValueMillis()
			);
			vehicles.add(train);
		}

		if (!trainsToRemove.isEmpty()) {
			trainsToRemove.forEach(vehicles::remove);
		}
	}

	public boolean isValidVehicle(int spacing) {
		return Math.max(2, railLength) >= spacing;
	}

	public int getMaxVehicles() {
		return maxVehicles;
	}

	public boolean getIsManual() {
		return isManual;
	}

	public double getMaxManualSpeed() {
		return maxManualSpeed;
	}

	public boolean getUnlimitedVehicles() {
		return unlimitedVehicles;
	}

	public void clearVehicles() {
		vehicles.clear();
	}

	private ObjectArrayList<PathData> getPathMainRoute() {
		return area == null ? new ObjectArrayList<>() : area.path;
	}

	private String getDepotName() {
		return area == null ? "" : area.name;
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
		}
		totalVehicleLength = tempVehicleLength;
	}

	private void finishGeneratingPath() {
		if (sidingPathFinderSidingToMainRoute.isEmpty() && sidingPathFinderMainRouteToSiding.isEmpty()) {
			vehicles.clear();
			if (pathSidingToMainRoute.isEmpty()) {
				generateDefaultPath();
			} else {
				Main.LOGGER.info(String.format("Path successfully generated for %s siding %s", getDepotName(), name));
			}
			generateDistances();
		}
	}

	private void generateDefaultPath() {
		pathSidingToMainRoute.clear();
		pathMainRouteToSiding.clear();

		final Position position1 = positions.left();
		final Position position2 = positions.right();
		final Rail rail = DataCache.tryGet(simulator.dataCache.positionToRailConnections, position1, position2);
		if (rail != null) {
			pathSidingToMainRoute.add(new PathData(rail, -1, position1, position2));
		}

		vehicles.add(new Train(
				id, id, transportMode, railLength, vehicleCars, totalVehicleLength,
				pathSidingToMainRoute, getPathMainRoute(), pathMainRouteToSiding, distances,
				acceleration, isManual, maxManualSpeed, getTimeValueMillis()
		));
	}

	private void generateDistances() {
		distances.clear();
		timeSegments.clear();

		final ObjectArrayList<PathData> path = new ObjectArrayList<>();
		path.addAll(pathSidingToMainRoute);
		path.addAll(getPathMainRoute());
		path.addAll(pathMainRouteToSiding);

		double distanceSum = 0;
		for (final PathData pathData : path) {
			distanceSum += pathData.rail.getLength();
			distances.add(distanceSum);
		}

		if (path.size() != 1) {
			vehicles.removeIf(train -> (train.id == id) == unlimitedVehicles);
		}

		double distanceSum1 = 0;
		final List<Double> stoppingDistances = new ArrayList<>();
		for (final PathData pathData : path) {
			distanceSum1 += pathData.rail.getLength();
			if (pathData.dwellTimeMillis > 0) {
				stoppingDistances.add(distanceSum1);
			}
		}

		double railProgress = (railLength + totalVehicleLength) / 2;
		double nextStoppingDistance = 0;
		double speed = 0;
		double time = 0;
		double timeOld = 0;
		long savedRailBaseIdOld = 0;
		double distanceSum2 = 0;
		for (int i = 0; i < path.size(); i++) {
			if (railProgress >= nextStoppingDistance) {
				if (stoppingDistances.isEmpty()) {
					nextStoppingDistance = distanceSum1;
				} else {
					nextStoppingDistance = stoppingDistances.remove(0);
				}
			}

			final PathData pathData = path.get(i);
			final double railSpeed = pathData.rail.canAccelerate ? pathData.rail.speedLimitMetersPerMillisecond : Math.max(speed, transportMode.defaultSpeedMetersPerMillisecond);
			distanceSum2 += pathData.rail.getLength();

			while (railProgress < distanceSum2) {
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

				if (timeSegments.isEmpty() || timeSegments.get(timeSegments.size() - 1).speedChange != speedChange) {
					timeSegments.add(new TimeSegment(railProgress, speed, time, speedChange, acceleration));
				}

				railProgress = Math.min(railProgress + speed, distanceSum2);
				time++;

				final TimeSegment timeSegment = timeSegments.get(timeSegments.size() - 1);
				timeSegment.endRailProgress = railProgress;
				timeSegment.endTime = time;
				timeSegment.savedRailBaseId = nextStoppingDistance != distanceSum1 && railProgress == distanceSum2 && pathData.dwellTimeMillis > 0 ? pathData.savedRailBaseId : 0;
			}

			time += pathData.dwellTimeMillis * 5;

			if (pathData.savedRailBaseId != 0) {
				if (savedRailBaseIdOld != 0) {
					if (!platformTimes.containsKey(savedRailBaseIdOld)) {
						platformTimes.put(savedRailBaseIdOld, new HashMap<>());
					}
					platformTimes.get(savedRailBaseIdOld).put(pathData.savedRailBaseId, time - timeOld);
				}
				savedRailBaseIdOld = pathData.savedRailBaseId;
				timeOld = time;
			}

			time += pathData.dwellTimeMillis * 5;

			if (i + 1 < path.size() && pathData.isOppositeRail(path.get(i + 1))) {
				railProgress += totalVehicleLength;
			}
		}
	}

	public static double getRailLength(double rawRailLength) {
		return Utilities.round(rawRailLength, 3);
	}

	public static class TimeSegment {

		public double endRailProgress;
		public long savedRailBaseId;
		public long routeId;
		public int currentStationIndex;
		public double endTime;

		public final double startRailProgress;
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

		public double getTime(double railProgress) {
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
