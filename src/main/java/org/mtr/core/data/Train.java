package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.Int2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.path.PathData;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.util.UUID;

public class Train extends NameColorDataBase {

	private double speed;
	private double railProgress;
	private boolean doorTarget;
	private double doorValue;
	private int elapsedDwellMillis;
	private int nextStoppingIndex;
	private boolean reversed;
	private boolean isOnRoute = false;
	private boolean isCurrentlyManual;
	private int manualNotch;
	private int departureIndex = -1;

	public final double railLength;
	public final ObjectImmutableList<VehicleCar> vehicleCars;
	public final double totalVehicleLength;
	public final int vehicleCarCount;

	public final ObjectImmutableList<PathData> path;
	public final int repeatIndex1;
	public final int repeatIndex2;

	public final double acceleration;
	public final boolean isManualAllowed;
	public final double maxManualSpeed;
	public final int manualToAutomaticTime;

	private final Siding siding;
	private final ObjectOpenHashSet<UUID> ridingEntities = new ObjectOpenHashSet<>();
	private final double totalDistance;

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
	private static final String KEY_DEPARTURE_INDEX = "departure_index";
	private static final String KEY_RIDING_ENTITIES = "riding_entities";

	public Train(
			long id, Siding siding, TransportMode transportMode, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, int manualToAutomaticTime
	) {
		super(id, transportMode);

		this.siding = siding;
		this.railLength = Siding.getRailLength(railLength);

		this.vehicleCars = new ObjectImmutableList<>(vehicleCars);
		vehicleCarCount = this.vehicleCars.size();
		this.totalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);

		path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		repeatIndex1 = pathSidingToMainRoute.size();
		repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;

		this.acceleration = roundAcceleration(acceleration);
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;
		totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).endDistance;
	}

	public Train(
			Siding siding, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, int manualToAutomaticTime, ReaderBase readerBase
	) {
		super(readerBase);

		this.siding = siding;
		this.railLength = Siding.getRailLength(railLength);

		this.vehicleCars = new ObjectImmutableList<>(vehicleCars);
		vehicleCarCount = this.vehicleCars.size();
		this.totalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);

		path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		repeatIndex1 = pathSidingToMainRoute.size();
		repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;

		this.acceleration = roundAcceleration(acceleration);
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;
		totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).endDistance;

		updateData(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);

		readerBase.unpackDouble(KEY_SPEED, value -> speed = value);
		readerBase.unpackDouble(KEY_RAIL_PROGRESS, value -> railProgress = value);
		readerBase.unpackInt(KEY_ELAPSED_DWELL_MILLIS, value -> elapsedDwellMillis = value);
		readerBase.unpackInt(KEY_NEXT_STOPPING_INDEX, value -> nextStoppingIndex = value);
		readerBase.unpackBoolean(KEY_REVERSED, value -> reversed = value);
		readerBase.unpackBoolean(KEY_IS_ON_ROUTE, value -> isOnRoute = value);
		readerBase.unpackBoolean(KEY_IS_CURRENTLY_MANUAL, value -> isCurrentlyManual = value);
		readerBase.unpackInt(KEY_DEPARTURE_INDEX, value -> departureIndex = value);
		readerBase.iterateStringArray(KEY_RIDING_ENTITIES, value -> ridingEntities.add(UUID.fromString(value)));
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		super.toMessagePack(writerBase);

		writerBase.writeDouble(KEY_SPEED, speed);
		writerBase.writeDouble(KEY_RAIL_PROGRESS, railProgress);
		writerBase.writeInt(KEY_ELAPSED_DWELL_MILLIS, elapsedDwellMillis);
		writerBase.writeInt(KEY_NEXT_STOPPING_INDEX, nextStoppingIndex);
		writerBase.writeBoolean(KEY_REVERSED, reversed);
		writerBase.writeBoolean(KEY_IS_ON_ROUTE, isOnRoute);
		writerBase.writeBoolean(KEY_IS_CURRENTLY_MANUAL, isCurrentlyManual);
		writerBase.writeInt(KEY_DEPARTURE_INDEX, departureIndex);
		final WriterBase.Array writerBaseArray = writerBase.writeArray(KEY_RIDING_ENTITIES, ridingEntities.size());
		ridingEntities.forEach(uuid -> writerBaseArray.writeString(uuid.toString()));
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 9;
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

	public void writeVehiclePositions(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		writeVehiclePositions(Utilities.getIndexFromConditionalList(path, railProgress), vehiclePositions);
	}

	public void writeVehiclePositions(int currentIndex, Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		if (isOnRoute && currentIndex >= 0) {
			int index = currentIndex;
			while (true) {
				final PathData pathData = path.get(index);
				final double start = Math.max(pathData.startDistance, railProgress - totalVehicleLength);
				final double end = Math.min(pathData.endDistance, railProgress);
				if (end - start > 0.01) {
					DataCache.put(vehiclePositions, pathData.getOrderedPosition1(), pathData.getOrderedPosition2(), vehiclePosition -> {
						final VehiclePosition newVehiclePosition = vehiclePosition == null ? new VehiclePosition() : vehiclePosition;
						newVehiclePosition.addSegment(pathData.reversePositions ? end : start, pathData.reversePositions ? start : end, id);
						return newVehiclePosition;
					}, Object2ObjectAVLTreeMap::new);
				}
				index--;
				if (index < 0 || railProgress - totalVehicleLength >= pathData.startDistance) {
					break;
				}
			}
		}
	}

	public final void simulateTrain(long millisElapsed, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, Int2LongAVLTreeMap vehicleTimesAlongRoute) {
		try {
			if (nextStoppingIndex >= path.size()) {
				railProgress = (railLength + totalVehicleLength) / 2;
				return;
			}

			final boolean tempDoorTarget;
			final double tempDoorValue;

			if (!isOnRoute) {
				railProgress = (railLength + totalVehicleLength) / 2;
				reversed = false;
				tempDoorTarget = false;
				tempDoorValue = 0;
				speed = 0;
				nextStoppingIndex = 0;
				departureIndex = -1;

				if (isCurrentlyManual && manualNotch > 0) {
					startUp(-1);
				}
			} else {
				final double newAcceleration = acceleration * millisElapsed;
				final int currentIndex = Utilities.getIndexFromConditionalList(path, railProgress);

				if (repeatIndex2 == 0 && railProgress >= totalDistance - (railLength - totalVehicleLength) / 2 || !isManualAllowed && departureIndex < 0) {
					isOnRoute = false;
					manualNotch = -2;
					ridingEntities.clear();
					tempDoorTarget = false;
					tempDoorValue = 0;
				} else {
					if (speed <= 0) {
						speed = 0;

						final PathData currentPathData = currentIndex > 0 ? path.get(currentIndex - 1) : null;
						final PathData nextPathData = path.get(repeatIndex2 > 0 && currentIndex >= repeatIndex2 ? repeatIndex1 : currentIndex);
						final boolean isOpposite = currentPathData != null && currentPathData.isOppositeRail(nextPathData);
						final boolean railClear = railBlockedDistance(currentIndex, nextPathData.startDistance + (isOpposite ? totalVehicleLength : 0), 0, vehiclePositions) < 0;
						final int totalDwellMillis = currentPathData == null ? 0 : currentPathData.dwellTimeMillis;

						if (totalDwellMillis == 0) {
							tempDoorTarget = false;
						} else {
							if (elapsedDwellMillis + millisElapsed < totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY || railClear) {
								elapsedDwellMillis += millisElapsed;
							}
							tempDoorTarget = openDoors();
						}

						if ((isCurrentlyManual || elapsedDwellMillis >= totalDwellMillis) && railClear && (!isCurrentlyManual || manualNotch > 0)) {
							railProgress = nextPathData.startDistance;
							if (isOpposite) {
								railProgress += totalVehicleLength;
								reversed = !reversed;
							}
							startUp(departureIndex);
						}
					} else {
						final double safeStoppingDistance = 0.5 * speed * speed / acceleration;
						final double stoppingPoint;
						final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions);
						if (railBlockedDistance < 0) {
							stoppingPoint = path.get(nextStoppingIndex).endDistance;
						} else {
							stoppingPoint = railBlockedDistance + railProgress;
						}
						final double stoppingDistance = stoppingPoint - railProgress;

						if (!transportMode.continuousMovement && stoppingDistance < safeStoppingDistance) {
							speed = stoppingDistance <= 0 ? ACCELERATION_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, ACCELERATION_DEFAULT);
							manualNotch = -3;
						} else {
							if (manualNotch < -2) {
								manualNotch = 0;
							}
							if (isCurrentlyManual) {
								speed = Utilities.clamp(speed + manualNotch * newAcceleration / 2, 0, maxManualSpeed);
							} else {
								final double railSpeed = getRailSpeed(currentIndex);
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

						tempDoorTarget = transportMode.continuousMovement && openDoors();

						railProgress += speed * millisElapsed;
						if (!transportMode.continuousMovement && railProgress >= stoppingPoint) {
							railProgress = stoppingPoint;
							speed = 0;
							manualNotch = -2;
						}
					}

					tempDoorValue = Utilities.clamp(doorValue + (double) (millisElapsed * (doorTarget ? 1 : -1)) / DOOR_MOVE_TIME, 0, 1);
				}

				writeVehiclePositions(currentIndex, vehiclePositions.get(1));
			}

			vehicleTimesAlongRoute.put(departureIndex, Math.round(siding.getTimeAlongRoute(railProgress)) + elapsedDwellMillis);

			doorTarget = tempDoorTarget;
			doorValue = tempDoorValue;
			if (doorTarget || doorValue != 0) {
				manualNotch = -2;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startUp(int newDepartureIndex) {
		departureIndex = newDepartureIndex;
		isOnRoute = true;
		elapsedDwellMillis = 0;
		speed = ACCELERATION_DEFAULT;
		doorTarget = false;
		doorValue = 0;
		nextStoppingIndex = path.size() - 1;
		for (int i = Utilities.getIndexFromConditionalList(path, railProgress); i < path.size(); i++) {
			if (path.get(i).dwellTimeMillis > 0) {
				nextStoppingIndex = i;
				break;
			}
		}
	}

	public int getDepartureIndex() {
		return departureIndex;
	}

	protected boolean openDoors() {
		return doorTarget;
	}

	private double railBlockedDistance(int currentIndex, double checkRailProgress, double checkDistance, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions) {
		int index = currentIndex;
		while (true) {
			final PathData pathData = path.get(index);
			if (Utilities.isIntersecting(pathData.startDistance, pathData.endDistance, checkRailProgress, checkDistance + checkDistance)) {
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = DataCache.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						return vehiclePosition.isBlocked(
								id,
								pathData.reversePositions ? pathData.endDistance - checkRailProgress - checkDistance : checkRailProgress - pathData.startDistance,
								pathData.reversePositions ? pathData.endDistance - checkRailProgress : checkRailProgress + checkDistance - pathData.startDistance
						);
					}
				}
			}
			index++;
			if (index >= path.size()) {
				return -1;
			}
		}
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
			if (repeatInfinitely) {
				final PathData firstPathData = pathMainRoute.get(0);
				final PathData lastPathData = Utilities.getElement(pathMainRoute, -1);
				tempPath.add(new PathData(firstPathData, lastPathData.startDistance, lastPathData.startDistance + firstPathData.endDistance - firstPathData.startDistance));
			} else {
				tempPath.addAll(pathMainRouteToSiding);
			}
		}
		return new ObjectImmutableList<>(tempPath);
	}
}
