package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.Int2LongAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
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
import java.util.Random;

public class Siding extends SavedRailBase<Siding, Depot> {

	private double totalVehicleLength;
	private int maxVehicles;
	private double maxManualSpeed;
	private double acceleration = Train.ACCELERATION_DEFAULT;
	private PathData defaultPathData;

	public final Simulator simulator;
	public final double railLength;
	public final Schedule schedule;

	private final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Depot, Siding, Station, Platform>> sidingPathFinderSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Depot, Siding>> sidingPathFinderMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRouteToSiding = new ObjectArrayList<>();
	private final ObjectArraySet<Train> vehicles = new ObjectArraySet<>();
	private final ObjectImmutableList<MessagePackHelper> vehicleMessagePackHelpers;

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
		schedule = new Schedule(this, railLength, transportMode);
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
		schedule = new Schedule(this, railLength, transportMode);

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
					this, railLength, vehicleCars, totalVehicleLength,
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

		vehicles.forEach(train -> train.writeVehiclePositions(vehiclePositions));
	}

	public void simulateTrain(long millisElapsed, Object2LongAVLTreeMap<Position> vehiclePositions) {
		if (area == null) {
			return;
		}

		final boolean isManual = maxVehicles < 0;
		final int departureCount = schedule.getDepartureCount();
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
				final int departureIndex = train.getDepartureIndex();
				if (departureIndex < 0 || departureIndex >= departureCount || visitedDepartureIndices.contains(departureIndex)) {
					trainsToRemove.add(train);
				} else {
					visitedDepartureIndices.add(departureIndex);
				}
			} else {
				trainsAtDepot++;
				if (trainsAtDepot > 1) {
					trainsToRemove.add(train);
				} else if (!pathSidingToMainRoute.isEmpty() && !isManual) {
					final int departureIndex = schedule.matchDeparture();
					if (departureIndex >= 0 && departureIndex < departureCount) {
						train.startUp(departureIndex);
					}
				}
			}
		}

		if (defaultPathData != null && totalVehicleLength > 0 && spawnTrain && (maxVehicles == 0 || vehicles.size() < (isManual ? 1 : maxVehicles))) {
			vehicles.add(new Train(
					new Random().nextLong(), this, transportMode, railLength, vehicleCars, totalVehicleLength,
					pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData,
					area.repeatInfinitely, acceleration, isManual, maxManualSpeed, getTimeValueMillis()
			));
		}

		if (!trainsToRemove.isEmpty()) {
			trainsToRemove.forEach(vehicles::remove);
		}
	}

	public Int2LongAVLTreeMap getTimesAlongRoute() {
		final Int2LongAVLTreeMap timesAlongRoute = new Int2LongAVLTreeMap();
		vehicles.forEach(train -> timesAlongRoute.put(train.getDepartureIndex(), Math.round(train.getTimeAlongRoute())));
		return timesAlongRoute;
	}

	public int getMaxVehicles() {
		return maxVehicles;
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
	 * After a path is set, generate the distance and values.
	 * Should only be called during initialization and after a path is generated.
	 */
	private void generatePathDistancesAndTimeSegments() {
		vehicles.clear();
		pathMainRoute.clear();

		if (pathSidingToMainRoute.isEmpty() || area == null || area.path.isEmpty() || !area.repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			pathSidingToMainRoute.clear();
			pathMainRouteToSiding.clear();
			schedule.reset();
		} else {
			pathMainRoute.addAll(area.path);
			final boolean overlappingFromRepeating = SidingPathFinder.overlappingPaths(pathMainRoute, pathMainRoute);

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
			schedule.generateTimeSegments(pathSidingToMainRoute, pathMainRoute, totalVehicleLength, acceleration);
		}
	}

	public static double getRailLength(double rawRailLength) {
		return Utilities.round(rawRailLength, 3);
	}
}
