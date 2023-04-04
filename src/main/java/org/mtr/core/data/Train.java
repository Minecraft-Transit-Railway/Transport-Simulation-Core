package org.mtr.core.data;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleImmutableList;
import it.unimi.dsi.fastutil.objects.Object2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.msgpack.core.MessagePacker;
import org.mtr.core.path.PathData;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;
import org.mtr.core.tools.Vec3;

import java.io.IOException;
import java.util.UUID;

public class Train extends NameColorDataBase {

	private double speed;
	private double railProgress;
	private boolean doorTarget;
	private double doorValue;
	private double elapsedDwellMillis;
	private int nextStoppingIndex;
	private boolean reversed;
	private boolean isOnRoute = false;
	private boolean isCurrentlyManual;
	private int manualNotch;
	private boolean canDeploy;

	public final long sidingId;
	public final double railLength;
	public final ObjectImmutableList<VehicleCar> vehicleCars;
	public final double totalVehicleLength;
	public final int vehicleCarCount;

	public final ObjectImmutableList<PathData> path;
	public final DoubleImmutableList distances;
	public final int repeatIndex1;
	public final int repeatIndex2;

	public final double acceleration;
	public final boolean isManualAllowed;
	public final double maxManualSpeed;
	public final int manualToAutomaticTime;

	private final ObjectOpenHashSet<UUID> ridingEntities = new ObjectOpenHashSet<>();

	public static final double ACCELERATION_DEFAULT = 1D / 250000;
	public static final double MAX_ACCELERATION = 1D / 50000;
	public static final double MIN_ACCELERATION = 1D / 2500000;
	public static final int DOOR_MOVE_TIME = 64;
	protected static final int DOOR_DELAY = 20;

	private static final String KEY_SPEED = "speed";
	private static final String KEY_RAIL_PROGRESS = "rail_progress";
	private static final String KEY_ELAPSED_DWELL_MILLIS = "elapsed_dwell_millis";
	private static final String KEY_NEXT_STOPPING_INDEX = "next_stopping_index";
	private static final String KEY_REVERSED = "reversed";
	private static final String KEY_IS_CURRENTLY_MANUAL = "is_currently_manual";
	private static final String KEY_IS_ON_ROUTE = "is_on_route";
	private static final String KEY_RIDING_ENTITIES = "riding_entities";

	public Train(
			long id, long sidingId, TransportMode transportMode, double railLength, ObjectArrayList<VehicleCar> vehicleCars, double totalVehicleLength,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, DoubleArrayList distances, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, int manualToAutomaticTime
	) {
		super(id, transportMode);

		this.sidingId = sidingId;
		this.railLength = Siding.getRailLength(railLength);

		this.vehicleCars = new ObjectImmutableList<>(vehicleCars);
		vehicleCarCount = this.vehicleCars.size();
		this.totalVehicleLength = totalVehicleLength;

		path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		this.distances = new DoubleImmutableList(distances);
		repeatIndex1 = repeatInfinitely ? pathSidingToMainRoute.size() : 0;
		repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;

		this.acceleration = roundAcceleration(acceleration);
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;
	}

	public <T extends ReaderBase<U, T>, U> Train(
			long sidingId, double railLength, ObjectArrayList<VehicleCar> vehicleCars, double totalVehicleLength,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, DoubleArrayList distances, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, int manualToAutomaticTime, T readerBase
	) {
		super(readerBase);

		this.sidingId = sidingId;
		this.railLength = Siding.getRailLength(railLength);

		this.vehicleCars = new ObjectImmutableList<>(vehicleCars);
		vehicleCarCount = this.vehicleCars.size();
		this.totalVehicleLength = totalVehicleLength;

		path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		this.distances = new DoubleImmutableList(distances);
		repeatIndex1 = repeatInfinitely ? pathSidingToMainRoute.size() : 0;
		repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;

		this.acceleration = roundAcceleration(acceleration);
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;

		updateData(readerBase);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		super.updateData(readerBase);

		readerBase.unpackDouble(KEY_SPEED, value -> speed = value);
		readerBase.unpackDouble(KEY_RAIL_PROGRESS, value -> railProgress = value);
		readerBase.unpackDouble(KEY_ELAPSED_DWELL_MILLIS, value -> elapsedDwellMillis = value);
		readerBase.unpackInt(KEY_NEXT_STOPPING_INDEX, value -> nextStoppingIndex = value);
		readerBase.unpackBoolean(KEY_REVERSED, value -> reversed = value);
		readerBase.unpackBoolean(KEY_IS_ON_ROUTE, value -> isOnRoute = value);
		readerBase.unpackBoolean(KEY_IS_CURRENTLY_MANUAL, value -> isCurrentlyManual = value);
		readerBase.iterateStringArray(KEY_RIDING_ENTITIES, value -> ridingEntities.add(UUID.fromString(value)));
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_SPEED).packDouble(speed);
		messagePacker.packString(KEY_RAIL_PROGRESS).packDouble(railProgress);
		messagePacker.packString(KEY_ELAPSED_DWELL_MILLIS).packDouble(elapsedDwellMillis);
		messagePacker.packString(KEY_NEXT_STOPPING_INDEX).packLong(nextStoppingIndex);
		messagePacker.packString(KEY_REVERSED).packBoolean(reversed);
		messagePacker.packString(KEY_IS_ON_ROUTE).packBoolean(isOnRoute);
		messagePacker.packString(KEY_IS_CURRENTLY_MANUAL).packBoolean(isCurrentlyManual);
		messagePacker.packString(KEY_RIDING_ENTITIES).packArrayHeader(ridingEntities.size());
		for (final UUID uuid : ridingEntities) {
			messagePacker.packString(uuid.toString());
		}
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 8;
	}

	@Override
	protected final boolean hasTransportMode() {
		return true;
	}

	public final boolean getIsOnRoute() {
		return isOnRoute;
	}

	public final double getRailProgress() {
		return railProgress;
	}

	public final boolean closeToDepot() {
		return !isOnRoute || railProgress < totalVehicleLength + railLength;
	}

	public final boolean isCurrentlyManual() {
		return isCurrentlyManual;
	}

	public boolean changeManualSpeed(boolean isAccelerate) {
		if (doorValue == 0 && isAccelerate && manualNotch >= -2 && manualNotch < 2) {
			manualNotch++;
			return true;
		} else if (!isAccelerate && manualNotch > -2) {
			manualNotch--;
			return true;
		} else {
			return false;
		}
	}

	public boolean toggleDoors() {
		if (speed == 0) {
			doorTarget = !doorTarget;
			manualNotch = -2;
			return true;
		} else {
			doorTarget = false;
			return false;
		}
	}

	public final int getIndex(double railProgressOffset, boolean roundDown) {
		for (int i = 0; i < path.size(); i++) {
			final double tempDistance = distances.getDouble(i);
			if (railProgress + railProgressOffset < tempDistance || roundDown && railProgress + railProgressOffset == tempDistance) {
				return i;
			}
		}
		return path.size() - 1;
	}

	public final double getRailSpeed(int railIndex) {
		final Rail thisRail = path.get(railIndex).rail;
		final double railSpeed;
		if (thisRail.canAccelerate) {
			railSpeed = thisRail.speedLimitMetersPerMillisecond;
		} else {
			final Rail lastRail = railIndex > 0 ? path.get(railIndex - 1).rail : thisRail;
			railSpeed = Math.max(lastRail.canAccelerate ? lastRail.speedLimitMetersPerMillisecond : transportMode.defaultSpeedMetersPerMillisecond, speed);
		}
		return railSpeed;
	}

	public final double getSpeed() {
		return speed;
	}

	public final double getDoorValue() {
		return doorValue;
	}

	public final double getElapsedDwellMillis() {
		return elapsedDwellMillis;
	}

	public final boolean isReversed() {
		return reversed;
	}

	public final boolean isOnRoute() {
		return isOnRoute;
	}

	public int getTotalDwellMillis() {
		return path.get(nextStoppingIndex).dwellTimeMillis;
	}

	public void deployTrain() {
		canDeploy = true;
	}

	public void writeVehiclePositions(Object2LongAVLTreeMap<Position> vehiclePositions) {
		if (isOnRoute) {
			vehiclePositions.put(getRailPositionRounded(0), id);
			double railProgressOffset = 0;
			for (final VehicleCar vehicleCar : vehicleCars) {
				railProgressOffset -= vehicleCar.length;
				vehiclePositions.put(getRailPositionRounded(railProgressOffset), id);
			}
		}
	}

	public final void simulateTrain(long millisElapsed, Depot depot, Object2LongAVLTreeMap<Position> vehiclePositions) {
		try {
			if (nextStoppingIndex >= path.size()) {
				railProgress = (railLength + totalVehicleLength) / 2;
				return;
			}

			final boolean tempDoorOpen;
			final double tempDoorValue;
			final int totalDwellMillis = getTotalDwellMillis();

			if (!isOnRoute) {
				railProgress = (railLength + totalVehicleLength) / 2;
				reversed = false;
				tempDoorOpen = false;
				tempDoorValue = 0;
				speed = 0;
				nextStoppingIndex = 0;

				if (!isCurrentlyManual && canDeploy(depot) || isCurrentlyManual && manualNotch > 0) {
					startUp();
				}
			} else {
				final double newAcceleration = acceleration * millisElapsed;

				if (railProgress >= distances.getDouble(distances.size() - 1) - (railLength - totalVehicleLength) / 2) {
					isOnRoute = false;
					manualNotch = -2;
					ridingEntities.clear();
					tempDoorOpen = false;
					tempDoorValue = 0;
				} else {
					if (speed <= 0) {
						speed = 0;

						final boolean railClear = railBlockedDistance(isOppositeRail(), 0, vehiclePositions) < 0;

						if (totalDwellMillis == 0) {
							tempDoorOpen = false;
						} else {
							if (elapsedDwellMillis == 0 && isRepeat() && getIndex(0, false) >= repeatIndex2 && distances.size() > repeatIndex1) {
								railProgress = distances.getDouble(repeatIndex1);
							}

							if (elapsedDwellMillis + millisElapsed < totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY || railClear) {
								elapsedDwellMillis += millisElapsed;
							}

							tempDoorOpen = openDoors();
						}

						if ((isCurrentlyManual || elapsedDwellMillis >= totalDwellMillis) && railClear && (!isCurrentlyManual || manualNotch > 0)) {
							startUp();
						}
					} else {
						final double safeStoppingDistance = 0.5 * speed * speed / acceleration;
						final double stoppingDistance;
						final int railBlockedDistance = railBlockedDistance(false, (int) safeStoppingDistance, vehiclePositions);
						if (railBlockedDistance < 0) {
							stoppingDistance = distances.getDouble(nextStoppingIndex) - railProgress;
						} else {
							stoppingDistance = railBlockedDistance;
						}

						if (!transportMode.continuousMovement && stoppingDistance < safeStoppingDistance) {
							speed = stoppingDistance <= 0 ? Train.ACCELERATION_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, Train.ACCELERATION_DEFAULT);
							manualNotch = -3;
						} else {
							if (manualNotch < -2) {
								manualNotch = 0;
							}
							if (isCurrentlyManual) {
								speed = Utilities.clamp(speed + manualNotch * newAcceleration / 2, 0, maxManualSpeed);
							} else {
								final double railSpeed = getRailSpeed(getIndex(0, false));
								if (speed < railSpeed) {
									speed = Math.min(speed + newAcceleration, railSpeed);
									manualNotch = 2;
								} else if (speed > railSpeed) {
									speed = Math.max(speed - newAcceleration, railSpeed);
									manualNotch = -2;
								} else {
									manualNotch = 0;
								}
							}
						}

						tempDoorOpen = transportMode.continuousMovement && openDoors();
					}

					railProgress += speed * millisElapsed;
					if (!transportMode.continuousMovement && railProgress > distances.getDouble(nextStoppingIndex)) {
						railProgress = distances.getDouble(nextStoppingIndex);
						speed = 0;
						manualNotch = -2;
					}

					tempDoorValue = Utilities.clamp(doorValue + (double) (millisElapsed * (doorTarget ? 1 : -1)) / DOOR_MOVE_TIME, 0, 1);
				}
			}

			doorTarget = tempDoorOpen;
			doorValue = tempDoorValue;
			if (doorTarget || doorValue != 0) {
				manualNotch = -2;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void startUp() {
		canDeploy = false;
		isOnRoute = true;
		elapsedDwellMillis = 0;
		speed = Train.ACCELERATION_DEFAULT;
		if (isOppositeRail()) {
			railProgress += totalVehicleLength;
			reversed = !reversed;
		}
		nextStoppingIndex = getNextStoppingIndex();
		doorTarget = false;
		doorValue = 0;
	}

	protected boolean canDeploy(Depot depot) {
		if (path.size() > 1 && depot != null) {
			depot.requestDeploy(sidingId, this);
		}
		return canDeploy;
	}

	protected boolean openDoors() {
		return doorTarget;
	}

	protected boolean isRepeat() {
		return repeatIndex1 > 0 && repeatIndex2 > 0;
	}

	private int railBlockedDistance(boolean isOppositeRail, int checkDistance, Object2LongAVLTreeMap<Position> vehiclePositions) {
		for (int i = 2; i <= checkDistance + transportMode.stoppingSpace; i++) {
			final long blockedId = vehiclePositions.getLong(getRailPositionRounded(railProgress + (isOppositeRail ? totalVehicleLength : 0) + i));
			if (blockedId != 0 && id != blockedId) {
				return i;
			}
		}
		return -1;
	}

	private boolean isOppositeRail() {
		return path.size() > nextStoppingIndex + 1 && railProgress == distances.getDouble(nextStoppingIndex) && path.get(nextStoppingIndex).isOppositeRail(path.get(nextStoppingIndex + 1));
	}

	private int getNextStoppingIndex() {
		final int headIndex = getIndex(0, false);
		for (int i = headIndex; i < path.size(); i++) {
			if (path.get(i).dwellTimeMillis > 0) {
				return i;
			}
		}
		return path.size() - 1;
	}

	private Vec3 getRailPosition(double railProgressOffset) {
		final int index = getIndex(railProgressOffset, true);
		return path.get(index).rail.getPosition(railProgress + railProgressOffset - (index == 0 ? 0 : distances.getDouble(index - 1)));
	}

	private Position getRailPositionRounded(double railProgressOffset) {
		return new Position(getRailPosition(railProgressOffset));
	}

	public static double roundAcceleration(double acceleration) {
		final double tempAcceleration = Utilities.round(acceleration, 8);
		return tempAcceleration <= 0 ? ACCELERATION_DEFAULT : Utilities.clamp(tempAcceleration, MIN_ACCELERATION, MAX_ACCELERATION);
	}

	private static ObjectImmutableList<PathData> createPathData(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, boolean repeatInfinitely, PathData defaultPathData) {
		final ObjectArrayList<PathData> tempPath = new ObjectArrayList<>();
		if (pathSidingToMainRoute.isEmpty() || pathMainRoute.isEmpty() || !repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			tempPath.add(defaultPathData);
		} else {
			tempPath.addAll(pathSidingToMainRoute);
			tempPath.addAll(pathMainRoute);
			tempPath.addAll(pathMainRouteToSiding);
		}
		return new ObjectImmutableList<>(tempPath);
	}
}
