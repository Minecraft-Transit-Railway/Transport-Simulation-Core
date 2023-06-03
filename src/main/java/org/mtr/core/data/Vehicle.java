package org.mtr.core.data;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.VehicleSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public final class Vehicle extends VehicleSchema {

	private boolean doorTarget;
	private double doorValue;
	private int manualNotch;

	public final VehicleExtraData vehicleExtraData;
	private final Siding siding;

	public static final double ACCELERATION_DEFAULT = 1D / 250000;
	public static final double MAX_ACCELERATION = 1D / 50000;
	public static final double MIN_ACCELERATION = 1D / 2500000;
	public static final int DOOR_MOVE_TIME = 64;
	private static final int DOOR_DELAY = 20;

	public Vehicle(
			Siding siding, Simulator simulator, TransportMode transportMode, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime
	) {
		super(transportMode, simulator);
		this.siding = siding;
		isCurrentlyManual = isManualAllowed;
		vehicleExtraData = getVehicleExtraData(railLength, vehicleCars, pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData, repeatInfinitely, acceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime);
	}

	public Vehicle(
			Siding siding, Simulator simulator, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, ReaderBase readerBase
	) {
		super(readerBase, simulator);
		this.siding = siding;
		isCurrentlyManual = isManualAllowed;
		vehicleExtraData = getVehicleExtraData(railLength, vehicleCars, pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, defaultPathData, repeatInfinitely, acceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime);
		updateData(readerBase);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public boolean getIsOnRoute() {
		return railProgress > vehicleExtraData.getDefaultPosition();
	}

	public boolean closeToDepot() {
		return !getIsOnRoute() || railProgress < vehicleExtraData.getTotalVehicleLength() + vehicleExtraData.getRailLength();
	}

	public boolean isCurrentlyManual() {
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

	public double getRailSpeed(int railIndex) {
		final Rail thisRail = vehicleExtraData.newPath.get(railIndex).getRail();
		final double railSpeed;
		if (thisRail.canAccelerate()) {
			railSpeed = thisRail.speedLimitMetersPerMillisecond;
		} else {
			final Rail lastRail = railIndex > 0 ? vehicleExtraData.newPath.get(railIndex - 1).getRail() : thisRail;
			railSpeed = Math.max(lastRail.canAccelerate() ? lastRail.speedLimitMetersPerMillisecond : transportMode.defaultSpeedMetersPerMillisecond, speed);
		}
		return railSpeed;
	}

	public void writeVehiclePositions(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		writeVehiclePositions(Utilities.getIndexFromConditionalList(vehicleExtraData.newPath, railProgress), vehiclePositions);
	}

	public void simulateTrain(long millisElapsed, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, Long2LongAVLTreeMap vehicleTimesAlongRoute) {
		try {
			if (nextStoppingIndex >= vehicleExtraData.newPath.size()) {
				railProgress = vehicleExtraData.getDefaultPosition();
				return;
			}

			final boolean tempDoorTarget;
			final double tempDoorValue;

			if (!getIsOnRoute()) {
				railProgress = vehicleExtraData.getDefaultPosition();
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
				final double newAcceleration = vehicleExtraData.getAcceleration() * millisElapsed;
				final int currentIndex = Utilities.getIndexFromConditionalList(vehicleExtraData.newPath, railProgress);

				if (vehicleExtraData.getRepeatIndex2() == 0 && railProgress >= vehicleExtraData.getTotalDistance() - (vehicleExtraData.getRailLength() - vehicleExtraData.getTotalVehicleLength()) / 2 || !vehicleExtraData.getIsManualAllowed() && departureIndex < 0) {
					railProgress = vehicleExtraData.getDefaultPosition();
					manualNotch = -2;
					ridingEntities.clear();
					tempDoorTarget = false;
					tempDoorValue = 0;
				} else {
					if (speed <= 0) {
						speed = 0;

						final PathData currentPathData = currentIndex > 0 ? vehicleExtraData.newPath.get(currentIndex - 1) : null;
						final PathData nextPathData = vehicleExtraData.newPath.get(vehicleExtraData.getRepeatIndex2() > 0 && currentIndex >= vehicleExtraData.getRepeatIndex2() ? vehicleExtraData.getRepeatIndex1() : currentIndex);
						final boolean isOpposite = currentPathData != null && currentPathData.isOppositeRail(nextPathData);
						final boolean railClear = railBlockedDistance(currentIndex, nextPathData.getStartDistance() + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0), 0, vehiclePositions) < 0;
						final long totalDwellMillis = currentPathData == null ? 0 : currentPathData.getDwellTime();

						if (totalDwellMillis == 0) {
							tempDoorTarget = false;
						} else {
							if (elapsedDwellTime + millisElapsed < totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY || railClear) {
								elapsedDwellTime += millisElapsed;
							}
							tempDoorTarget = openDoors();
						}

						if ((isCurrentlyManual || elapsedDwellTime >= totalDwellMillis) && railClear && (!isCurrentlyManual || manualNotch > 0)) {
							railProgress = nextPathData.getStartDistance();
							if (isOpposite) {
								railProgress += vehicleExtraData.getTotalVehicleLength();
								reversed = !reversed;
							}
							startUp(departureIndex);
						}
					} else {
						final double safeStoppingDistance = 0.5 * speed * speed / vehicleExtraData.getAcceleration();
						final double stoppingPoint;
						final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions);
						if (railBlockedDistance < 0) {
							stoppingPoint = vehicleExtraData.newPath.get((int) nextStoppingIndex).getEndDistance();
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
								speed = Utilities.clamp(speed + manualNotch * newAcceleration / 2, 0, vehicleExtraData.getMaxManualSpeed());
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

			vehicleTimesAlongRoute.put(departureIndex, Math.round(siding.getTimeAlongRoute(railProgress)) + (long) elapsedDwellTime);

			doorTarget = tempDoorTarget;
			doorValue = tempDoorValue;
			if (doorTarget || doorValue != 0) {
				manualNotch = -2;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startUp(long newDepartureIndex) {
		departureIndex = newDepartureIndex;
		railProgress += ACCELERATION_DEFAULT;
		elapsedDwellTime = 0;
		speed = ACCELERATION_DEFAULT;
		doorTarget = false;
		doorValue = 0;
		nextStoppingIndex = vehicleExtraData.newPath.size() - 1;
		for (int i = Utilities.getIndexFromConditionalList(vehicleExtraData.newPath, railProgress); i < vehicleExtraData.newPath.size(); i++) {
			if (vehicleExtraData.newPath.get(i).getDwellTime() > 0) {
				nextStoppingIndex = i;
				break;
			}
		}
	}

	public long getDepartureIndex() {
		return departureIndex;
	}

	public boolean openDoors() {
		return doorTarget;
	}

	/**
	 * Indicate which portions of each path segment are occupied by this vehicle.
	 * Also checks if the vehicle has just entered or left a client's view area and sends a socket update.
	 */
	private void writeVehiclePositions(int currentIndex, Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		final Position[] minMaxPositions = {null, null};

		if (getIsOnRoute() && currentIndex >= 0) {
			int index = currentIndex;
			while (true) {
				final PathData pathData = vehicleExtraData.newPath.get(index);
				final double start = Math.max(pathData.getStartDistance(), railProgress - vehicleExtraData.getTotalVehicleLength());
				final double end = Math.min(pathData.getEndDistance(), railProgress);

				if (end - start > 0.01) {
					final Position position1 = pathData.getOrderedPosition1();
					final Position position2 = pathData.getOrderedPosition2();
					DataCache.put(vehiclePositions, position1, position2, vehiclePosition -> {
						final VehiclePosition newVehiclePosition = vehiclePosition == null ? new VehiclePosition() : vehiclePosition;
						newVehiclePosition.addSegment(pathData.reversePositions ? end : start, pathData.reversePositions ? start : end, id);
						return newVehiclePosition;
					}, Object2ObjectAVLTreeMap::new);
					minMaxPositions[0] = Position.getMin(minMaxPositions[0], Position.getMin(position1, position2));
					minMaxPositions[1] = Position.getMax(minMaxPositions[1], Position.getMax(position1, position2));
				}

				index--;

				if (index < 0 || railProgress - vehicleExtraData.getTotalVehicleLength() >= pathData.getStartDistance()) {
					break;
				}
			}
		}

		final double updateRadius = simulator.clientGroup.getUpdateRadius();
		final JsonObject responseObject = new JsonObject();
		final boolean[] updated = {false};

		simulator.clientGroup.iterateClients(client -> {
			final boolean hasVehicle = client.visibleVehicles.contains(this);
			final boolean inArea = minMaxPositions[0] == null || minMaxPositions[1] == null ? siding.area.inArea(client.getPosition(), updateRadius) : Utilities.isBetween(client.getPosition(), minMaxPositions[0], minMaxPositions[1], updateRadius);
			if (hasVehicle != inArea) {
				responseObject.addProperty(client.uuid.toString(), inArea);
				updated[0] = true;
				if (inArea) {
					client.visibleVehicles.add(this);
				} else {
					client.visibleVehicles.remove(this);
				}
			}
		});

		if (updated[0]) {
			responseObject.add("vehicle", Utilities.getJsonObjectFromData(this));
			responseObject.add("vehicleData", Utilities.getJsonObjectFromData(vehicleExtraData));
			responseObject.add("depot", siding.area == null ? new JsonObject() : Utilities.getJsonObjectFromData(siding.area));
			simulator.clientGroup.sendToClient(responseObject);
		}
	}

	private double railBlockedDistance(int currentIndex, double checkRailProgress, double checkDistance, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions) {
		int index = currentIndex;
		while (true) {
			final PathData pathData = vehicleExtraData.newPath.get(index);
			if (Utilities.isIntersecting(pathData.getStartDistance(), pathData.getEndDistance(), checkRailProgress, checkDistance + checkDistance)) {
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = DataCache.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						return vehiclePosition.isBlocked(
								id,
								pathData.reversePositions ? pathData.getEndDistance() - checkRailProgress - checkDistance : checkRailProgress - pathData.getStartDistance(),
								pathData.reversePositions ? pathData.getEndDistance() - checkRailProgress : checkRailProgress + checkDistance - pathData.getStartDistance()
						);
					}
				}
			}
			index++;
			if (index >= vehicleExtraData.newPath.size()) {
				return -1;
			}
		}
	}

	public static double roundAcceleration(double acceleration) {
		final double tempAcceleration = Utilities.round(acceleration, 8);
		return tempAcceleration <= 0 ? ACCELERATION_DEFAULT : Utilities.clamp(tempAcceleration, MIN_ACCELERATION, MAX_ACCELERATION);
	}

	private static VehicleExtraData getVehicleExtraData(
			double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime
	) {
		final double newRailLength = Siding.getRailLength(railLength);
		final double newTotalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);
		final ObjectArrayList<PathData> path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		final long repeatIndex1 = pathSidingToMainRoute.size();
		final long repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;
		final double newAcceleration = roundAcceleration(acceleration);
		final double totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).getEndDistance();
		final double defaultPosition = (newRailLength + newTotalVehicleLength) / 2;
		return new VehicleExtraData(newRailLength, newTotalVehicleLength, repeatIndex1, repeatIndex2, newAcceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition, vehicleCars, path);
	}

	private static ObjectArrayList<PathData> createPathData(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, boolean repeatInfinitely, PathData defaultPathData) {
		final ObjectArrayList<PathData> tempPath = new ObjectArrayList<>();
		if (pathSidingToMainRoute.isEmpty() || pathMainRoute.isEmpty() || !repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			tempPath.add(defaultPathData);
		} else {
			tempPath.addAll(pathSidingToMainRoute);
			tempPath.addAll(pathMainRoute);
			if (repeatInfinitely) {
				final PathData firstPathData = pathMainRoute.get(0);
				final PathData lastPathData = Utilities.getElement(pathMainRoute, -1);
				tempPath.add(new PathData(firstPathData, lastPathData.getStartDistance(), lastPathData.getStartDistance() + firstPathData.getEndDistance() - firstPathData.getStartDistance()));
			} else {
				tempPath.addAll(pathMainRouteToSiding);
			}
		}
		return tempPath;
	}
}
