package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessagePacker;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.*;

public class Siding extends SavedRailBase<Siding, Depot> {

	private String vehicleId;
	private String baseVehicleType;
	private int vehicleCars;
	private boolean unlimitedVehicles;
	private int maxVehicles;
	private boolean isManual;
	private double maxManualSpeed;
	private int repeatIndex1;
	private int repeatIndex2;
	private double acceleration = Train.ACCELERATION_DEFAULT;

	public final double railLength;
	private final List<SidingPathFinder<Depot, Siding, Station, Platform>> sidingPathFinderSidingToMainRoute = new ArrayList<>();
	private final List<SidingPathFinder<Station, Platform, Depot, Siding>> sidingPathFinderMainRouteToSiding = new ArrayList<>();
	private final ObjectArrayList<PathData> pathSidingToMainRoute = new ObjectArrayList<>();
	private final ObjectArrayList<PathData> pathMainRouteToSiding = new ObjectArrayList<>();
	private final List<Double> distances = new ArrayList<>();
	private final List<TimeSegment> timeSegments = new ArrayList<>();
	private final Map<Long, Map<Long, Double>> platformTimes = new HashMap<>();
	private final Set<Train> vehicles = new HashSet<>();
	private final Set<MessagePackHelper> vehicleMessagePackHelpers = new HashSet<>();

	private static final String KEY_RAIL_LENGTH = "rail_length";
	private static final String KEY_BASE_VEHICLE_TYPE = "train_type";
	private static final String KEY_VEHICLE_ID = "train_custom_id";
	private static final String KEY_UNLIMITED_VEHICLES = "unlimited_trains";
	private static final String KEY_MAX_VEHICLES = "max_trains";
	private static final String KEY_IS_MANUAL = "is_manual";
	private static final String KEY_MAX_MANUAL_SPEED = "max_manual_speed_meters_per_millisecond";
	private static final String KEY_PATH_SIDING_TO_MAIN_ROUTE = "path_siding_to_main_route";
	private static final String KEY_PATH_MAIN_ROUTE_TO_SIDING = "path_main_route_to_siding";
	private static final String KEY_REPEAT_INDEX_1 = "repeat_index_1";
	private static final String KEY_REPEAT_INDEX_2 = "repeat_index_2";
	private static final String KEY_VEHICLES = "trains";
	private static final String KEY_ACCELERATION = "acceleration";

	public Siding(long id, TransportMode transportMode, Position pos1, Position pos2, double railLength) {
		super(id, transportMode, pos1, pos2);

		this.railLength = getRailLength(railLength);
		setVehicleDetails();
		unlimitedVehicles = transportMode.continuousMovement;
		acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.ACCELERATION_DEFAULT;
	}

	public Siding(TransportMode transportMode, Position pos1, Position pos2, double railLength) {
		super(transportMode, pos1, pos2);

		this.railLength = getRailLength(railLength);
		setVehicleDetails();
		unlimitedVehicles = transportMode.continuousMovement;
		acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.ACCELERATION_DEFAULT;
	}

	public Siding(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);

		railLength = getRailLength(messagePackHelper.getDouble(KEY_RAIL_LENGTH, 0));
		messagePackHelper.iterateArrayValue(KEY_VEHICLES, value -> vehicleMessagePackHelpers.add(new MessagePackHelper(MessagePackHelper.castMessagePackValueToSKMap(value))));
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);

		messagePackHelper.unpackString(KEY_BASE_VEHICLE_TYPE, value -> setVehicleDetails(messagePackHelper.getString(KEY_VEHICLE_ID, ""), value, false));
		messagePackHelper.unpackBoolean(KEY_UNLIMITED_VEHICLES, value -> unlimitedVehicles = transportMode.continuousMovement || value);
		messagePackHelper.unpackInt(KEY_MAX_VEHICLES, value -> maxVehicles = value);
		messagePackHelper.unpackBoolean(KEY_IS_MANUAL, value -> isManual = value);
		messagePackHelper.unpackDouble(KEY_MAX_MANUAL_SPEED, value -> maxManualSpeed = value);
		DataFixer.unpackMaxManualSpeed(messagePackHelper, value -> maxManualSpeed = value);
		messagePackHelper.unpackInt(KEY_REPEAT_INDEX_1, value -> repeatIndex1 = value);
		messagePackHelper.unpackInt(KEY_REPEAT_INDEX_2, value -> repeatIndex2 = value);
		messagePackHelper.unpackDouble(KEY_ACCELERATION, value -> acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.roundAcceleration(value));
		DataFixer.unpackAcceleration(messagePackHelper, value -> acceleration = transportMode.continuousMovement ? Train.MAX_ACCELERATION : Train.roundAcceleration(value));

		messagePackHelper.iterateArrayValue(KEY_PATH_SIDING_TO_MAIN_ROUTE, pathSection -> pathSidingToMainRoute.add(new PathData(new MessagePackHelper(MessagePackHelper.castMessagePackValueToSKMap(pathSection)))));
		messagePackHelper.iterateArrayValue(KEY_PATH_MAIN_ROUTE_TO_SIDING, pathSection -> pathMainRouteToSiding.add(new PathData(new MessagePackHelper(MessagePackHelper.castMessagePackValueToSKMap(pathSection)))));
	}

	@Override
	public void init() {
		vehicleMessagePackHelpers.forEach(messagePackHelper -> vehicles.add(new Train(
				id, railLength,
				pathSidingToMainRoute, area.path, pathMainRouteToSiding, distances,
				acceleration, isManual, maxManualSpeed, getTimeValueMillis(), messagePackHelper)
		));
		generateDistances();
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_RAIL_LENGTH).packDouble(railLength);
		messagePacker.packString(KEY_VEHICLE_ID).packString(vehicleId);
		messagePacker.packString(KEY_BASE_VEHICLE_TYPE).packString(baseVehicleType);
		messagePacker.packString(KEY_UNLIMITED_VEHICLES).packBoolean(unlimitedVehicles);
		messagePacker.packString(KEY_MAX_VEHICLES).packInt(maxVehicles);
		messagePacker.packString(KEY_IS_MANUAL).packBoolean(isManual);
		messagePacker.packString(KEY_MAX_MANUAL_SPEED).packDouble(maxManualSpeed);
		messagePacker.packString(KEY_REPEAT_INDEX_1).packInt(repeatIndex1);
		messagePacker.packString(KEY_REPEAT_INDEX_2).packInt(repeatIndex2);
		messagePacker.packString(KEY_ACCELERATION).packDouble(acceleration);
		MessagePackHelper.writeMessagePackDataset(messagePacker, pathSidingToMainRoute, KEY_PATH_SIDING_TO_MAIN_ROUTE);
		MessagePackHelper.writeMessagePackDataset(messagePacker, pathMainRouteToSiding, KEY_PATH_MAIN_ROUTE_TO_SIDING);
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 12;
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

	public String getVehicleId() {
		return vehicleId;
	}

	public double getAcceleration() {
		return acceleration;
	}

	public void generateRoute(int successfulSegmentsMain, Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, Platform firstPlatform, Platform lastPlatform, boolean repeatInfinitely, int cruisingAltitude) {
		if (area == null) {
			vehicles.clear();
		}

		pathSidingToMainRoute.clear();
		pathMainRouteToSiding.clear();
		sidingPathFinderSidingToMainRoute.clear();
		sidingPathFinderSidingToMainRoute.add(new SidingPathFinder<>(rails, this, firstPlatform, 0));
		sidingPathFinderMainRouteToSiding.clear();
		if (!repeatInfinitely) {
			sidingPathFinderMainRouteToSiding.add(new SidingPathFinder<>(rails, lastPlatform, this, successfulSegmentsMain + 1));
		}
	}

	public void tick(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		SidingPathFinder.findPathTick(rails, pathSidingToMainRoute, sidingPathFinderSidingToMainRoute, stopIndexSidingToMainRoute -> {
			SidingPathFinder.addPathData(rails, pathSidingToMainRoute, true, this, pathSidingToMainRoute, false, -1);
			finishGeneratingPath(rails);
		});
		SidingPathFinder.findPathTick(rails, pathMainRouteToSiding, sidingPathFinderMainRouteToSiding, stopIndexMainRouteToSiding -> finishGeneratingPath(rails));
	}

	public void simulateTrain(float ticksElapsed, DataCache dataCache, List<Map<UUID, Long>> trainPositions, SignalBlocks signalBlocks, Map<Long, List<ScheduleEntry>> schedulesForPlatform, Map<Long, Map<Position, TrainDelay>> trainDelays) {
		if (area == null) {
			return;
		}

		int trainsAtDepot = 0;
		boolean spawnTrain = true;

		final Set<Long> railProgressSet = new HashSet<>();
		final Set<Train> trainsToRemove = new HashSet<>();
		for (final Train train : vehicles) {
			train.simulateTrain(ticksElapsed, area);

			if (train.closeToDepot(train.spacing * vehicleCars)) {
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

		if (vehicleCars > 0 && (vehicles.isEmpty() || spawnTrain && (unlimitedVehicles || vehicles.size() <= maxVehicles))) {
			final Train train = new Train(
					unlimitedVehicles || maxVehicles > 0 ? new Random().nextLong() : id, id, railLength, baseVehicleType, vehicleId,
					pathSidingToMainRoute, area.path, pathMainRouteToSiding, distances,
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

	private void setVehicleDetails() {
		final String defaultVehicle = transportMode.toString().toLowerCase(Locale.ENGLISH) + "_1_1";
		setVehicleDetails(defaultVehicle, defaultVehicle, true);
	}

	private void setVehicleDetails(String trainId, String baseVehicleType, boolean force) {
		final int vehicleSpacing = VehicleType.getSpacing(baseVehicleType);
		if (force || isValidVehicle(vehicleSpacing)) {
			this.baseVehicleType = baseVehicleType.toLowerCase(Locale.ENGLISH);
			vehicleId = trainId.isEmpty() ? this.baseVehicleType : trainId.toLowerCase(Locale.ENGLISH);
			vehicleCars = getVehicleCars(transportMode, railLength, vehicleSpacing);
		} else {
			setVehicleDetails();
		}
	}

	private void finishGeneratingPath(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		if (sidingPathFinderSidingToMainRoute.isEmpty() && sidingPathFinderMainRouteToSiding.isEmpty()) {
			if (pathSidingToMainRoute.isEmpty() || pathMainRouteToSiding.isEmpty()) {
				generateDefaultPath(rails);
			}
			generateDistances();
		}
	}

	private void generateDefaultPath(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		vehicles.clear();
		pathSidingToMainRoute.clear();
		pathMainRouteToSiding.clear();

		final List<Position> orderedPositions = getOrderedPositions(new Position(0, 0, 0), false);
		final Position pos1 = orderedPositions.get(0);
		final Position pos2 = orderedPositions.get(1);
		if (Utilities.containsRail(rails, pos1, pos2)) {
			pathSidingToMainRoute.add(new PathData(rails.get(pos1).get(pos2), id, 0, pos1, pos2, -1));
		}

		vehicles.add(new Train(
				id, id, railLength, baseVehicleType, vehicleId,
				pathSidingToMainRoute, area.path, pathMainRouteToSiding, distances,
				acceleration, isManual, maxManualSpeed, getTimeValueMillis()
		));
	}

	private void generateDistances() {
		distances.clear();
		timeSegments.clear();

		final ObjectArrayList<PathData> path = new ObjectArrayList<>();
		path.addAll(pathSidingToMainRoute);
		path.addAll(area.path);
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

		final int spacing = VehicleType.getSpacing(baseVehicleType);
		double railProgress = (railLength + vehicleCars * spacing) / 2;
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
				railProgress += spacing * vehicleCars;
			}
		}
	}

	public static double getRailLength(double rawRailLength) {
		return Utilities.round(rawRailLength, 3);
	}

	public static int getVehicleCars(TransportMode transportMode, double railLength, int vehicleSpacing) {
		return Math.min(transportMode.maxLength, (int) Math.floor(getRailLength(railLength) / vehicleSpacing));
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
