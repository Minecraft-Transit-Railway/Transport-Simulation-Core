package org.mtr.core.data;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.generated.VehicleSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;
import org.mtr.core.tools.Vector;

import javax.annotation.Nullable;

public class Vehicle extends VehicleSchema {

	private double doorValue;
	private int manualNotch;

	public final VehicleExtraData vehicleExtraData;
	private final Siding siding;
	/**
	 * If a vehicle is clientside, don't open the doors or start up automatically. Always wait for a socket update instead.
	 */
	private final boolean isClientside;

	public static final int DOOR_MOVE_TIME = 64;
	private static final int DOOR_DELAY = 20;

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, boolean isClientside, TransportMode transportMode, Data data) {
		super(transportMode, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		isCurrentlyManual = vehicleExtraData.getIsManualAllowed();
		this.isClientside = isClientside;
	}

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, boolean isClientside, ReaderBase readerBase, Data data) {
		super(readerBase, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		isCurrentlyManual = vehicleExtraData.getIsManualAllowed();
		this.isClientside = isClientside;
		updateData(readerBase);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public boolean getIsOnRoute() {
		return railProgress > vehicleExtraData.getDefaultPosition();
	}

	public boolean getReversed() {
		return reversed;
	}

	public boolean closeToDepot() {
		return !getIsOnRoute() || railProgress < vehicleExtraData.getTotalVehicleLength() + vehicleExtraData.getRailLength();
	}

	public boolean changeSpeedManual(boolean isAccelerate) {
		if (isCurrentlyManual) {
			manualNotch += isAccelerate ? 1 : -1;
			return true;
		} else {
			return false;
		}
	}

	public boolean toggleDoorsManual() {
		if (isCurrentlyManual) {
			vehicleExtraData.toggleDoors();
			return true;
		} else {
			return false;
		}
	}

	public void initVehiclePositions(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		writeVehiclePositions(Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, railProgress), vehiclePositions);
	}

	public void simulate(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, @Nullable Long2LongAVLTreeMap vehicleTimesAlongRoute) {
		final int currentIndex;

		if (getIsOnRoute()) {
			if (vehicleExtraData.getRepeatIndex2() == 0 && railProgress >= vehicleExtraData.getTotalDistance() - (vehicleExtraData.getRailLength() - vehicleExtraData.getTotalVehicleLength()) / 2) {
				// if not repeat infinitely and the vehicle is reaching the end
				currentIndex = 0;
				railProgress = vehicleExtraData.getDefaultPosition();
				manualNotch = 0;
				ridingEntities.clear();
				vehicleExtraData.closeDoors();
			} else {
				// if vehicle is on route normally
				currentIndex = Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, railProgress);
				if (speed <= 0) {
					// if vehicle is stopped (at a platform or waiting for a signal)
					speed = 0;
					simulateAutomaticStopped(millisElapsed, vehiclePositions, currentIndex);
				} else {
					// if vehicle is moving normally
					simulateAutomaticMoving(millisElapsed, vehiclePositions, currentIndex);
				}
			}
		} else {
			currentIndex = 0;
			simulateInDepot();
		}

		doorValue = Utilities.clamp(doorValue + (double) (millisElapsed * vehicleExtraData.getDoorMultiplier()) / DOOR_MOVE_TIME, 0, 1);

		if (vehiclePositions != null) {
			writeVehiclePositions(currentIndex, vehiclePositions.get(1));
		}

		if (siding != null && vehicleTimesAlongRoute != null) {
			vehicleTimesAlongRoute.put(departureIndex, Math.round(siding.getTimeAlongRoute(railProgress)) + (long) elapsedDwellTime);
		}
	}

	public void startUp(long newDepartureIndex) {
		departureIndex = newDepartureIndex;
		railProgress += Siding.ACCELERATION_DEFAULT;
		elapsedDwellTime = 0;
		speed = Siding.ACCELERATION_DEFAULT;
		vehicleExtraData.closeDoors();
		nextStoppingIndex = vehicleExtraData.immutablePath.size() - 1;
		for (int i = Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, railProgress); i < vehicleExtraData.immutablePath.size(); i++) {
			if (vehicleExtraData.immutablePath.get(i).getDwellTime() > 0) {
				nextStoppingIndex = i;
				break;
			}
		}
	}

	public long getDepartureIndex() {
		return departureIndex;
	}

	public ObjectArrayList<ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>> getPositions() {
		final ObjectArrayList<ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>> positions = new ObjectArrayList<>();
		double railProgressOffset = 0;

		for (final VehicleCar vehicleCar : vehicleExtraData.getVehicleCars(reversed)) {
			final double length = vehicleCar.getLength();
			final double halfLength = length / 2;
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogiePositionsList = new ObjectArrayList<>();
			final ObjectObjectImmutablePair<Vector, Vector> bogiePositions1 = getBogiePositions(railProgress - railProgressOffset - halfLength - vehicleCar.getBogie1Position());
			if (bogiePositions1 == null) {
				return new ObjectArrayList<>();
			} else {
				bogiePositionsList.add(bogiePositions1);
			}

			if (!vehicleCar.hasOneBogie) {
				final ObjectObjectImmutablePair<Vector, Vector> bogiePositions2 = getBogiePositions(railProgress - railProgressOffset - halfLength - vehicleCar.getBogie2Position());
				if (bogiePositions2 == null) {
					return new ObjectArrayList<>();
				} else {
					bogiePositionsList.add(bogiePositions2);
				}
			}

			positions.add(bogiePositionsList);
			railProgressOffset += length;
		}

		return positions;
	}

	private void simulateInDepot() {
		railProgress = vehicleExtraData.getDefaultPosition();
		reversed = false;
		speed = 0;
		nextStoppingIndex = 0;
		departureIndex = -1;
		vehicleExtraData.closeDoors();

		if (isCurrentlyManual && manualNotch > 0) {
			startUp(-1);
		}
	}

	private void simulateAutomaticStopped(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex) {
		if (isClientside) {
			return;
		}

		final PathData currentPathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex - 1);
		final PathData nextPathData = Utilities.getElement(vehicleExtraData.immutablePath, vehicleExtraData.getRepeatIndex2() > 0 && currentIndex >= vehicleExtraData.getRepeatIndex2() ? vehicleExtraData.getRepeatIndex1() : currentIndex);
		final boolean isOpposite = currentPathData != null && nextPathData != null && currentPathData.isOppositeRail(nextPathData);
		final double nextStartDistance = nextPathData == null ? 0 : nextPathData.getStartDistance();
		final boolean railClear = railBlockedDistance(currentIndex, nextStartDistance + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0), 0, vehiclePositions) < 0;
		final long totalDwellMillis = currentPathData == null ? 0 : currentPathData.getDwellTime();
		vehicleExtraData.setStoppingPoint(railProgress);

		if (totalDwellMillis == 0) {
			vehicleExtraData.closeDoors();
		} else {
			final long doorCloseTime = totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY;

			if (Utilities.isBetween(elapsedDwellTime, DOOR_DELAY, doorCloseTime)) {
				vehicleExtraData.openDoors();
			} else {
				vehicleExtraData.closeDoors();
			}

			if (elapsedDwellTime + millisElapsed < doorCloseTime || railClear) {
				elapsedDwellTime += millisElapsed;
			}
		}

		if (elapsedDwellTime >= totalDwellMillis && railClear) {
			if (currentPathData != null && Math.abs(currentPathData.getEndDistance() - railProgress) < 0.01) {
				railProgress = nextStartDistance;
				if (isOpposite) {
					railProgress += vehicleExtraData.getTotalVehicleLength();
					reversed = !reversed;
				}
			}
			startUp(departureIndex);
		}
	}

	private void simulateAutomaticMoving(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex) {
		final double newAcceleration = vehicleExtraData.getAcceleration() * millisElapsed;
		final double safeStoppingDistance = 0.5 * speed * speed / vehicleExtraData.getAcceleration();
		final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions);
		final double stoppingPoint;

		if (isClientside) {
			stoppingPoint = vehicleExtraData.getStoppingPoint();
		} else if (railBlockedDistance < 0) {
			stoppingPoint = vehicleExtraData.immutablePath.get(Math.min((int) nextStoppingIndex, vehicleExtraData.immutablePath.size() - 1)).getEndDistance();
		} else {
			stoppingPoint = railBlockedDistance + railProgress;
		}

		vehicleExtraData.setStoppingPoint(stoppingPoint);
		final double stoppingDistance = stoppingPoint - railProgress;

		if (stoppingDistance < safeStoppingDistance) {
			speed = stoppingDistance <= 0 ? Siding.ACCELERATION_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, Siding.ACCELERATION_DEFAULT);
		} else {
			final double railSpeed = getRailSpeed(currentIndex);
			if (speed < railSpeed) {
				speed = Math.min(speed + newAcceleration, railSpeed);
			} else if (speed > railSpeed) {
				speed = Math.max(speed - newAcceleration, railSpeed);
			}
		}

		railProgress += speed * millisElapsed;
		if (railProgress >= stoppingPoint) {
			railProgress = stoppingPoint;
			speed = 0;
		}
	}

	private double getRailSpeed(int currentIndex) {
		final PathData thisPathData = vehicleExtraData.immutablePath.get(currentIndex);
		final double railSpeed;

		if (thisPathData.canAccelerate()) {
			railSpeed = thisPathData.getSpeedLimitMetersPerMillisecond();
		} else {
			final PathData lastPathData = currentIndex > 0 ? vehicleExtraData.immutablePath.get(currentIndex - 1) : thisPathData;
			railSpeed = Math.max(lastPathData.canAccelerate() ? lastPathData.getSpeedLimitMetersPerMillisecond() : transportMode.defaultSpeedMetersPerMillisecond, speed);
		}

		return railSpeed;
	}

	/**
	 * Indicate which portions of each path segment are occupied by this vehicle.
	 * Also checks if the vehicle needs to send a socket update:
	 * <ul>
	 * <li>Entered a client's view radius</li>
	 * <li>Left a client's view radius</li>
	 * <li>Started moving</li>
	 * <li>New stopping index or blocked rail</li>
	 * </ul>
	 */
	private void writeVehiclePositions(int currentIndex, Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		final Position[] minMaxPositions = {null, null};
		int index = currentIndex;

		while (index >= 0) {
			final PathData pathData = vehicleExtraData.immutablePath.get(index);

			if (railProgress - vehicleExtraData.getTotalVehicleLength() > pathData.getEndDistance()) {
				break;
			}

			final DoubleDoubleImmutablePair blockedBounds = getBlockedBounds(pathData, railProgress - vehicleExtraData.getTotalVehicleLength(), railProgress);

			if (blockedBounds.rightDouble() - blockedBounds.leftDouble() > 0.01) {
				final Position position1 = pathData.getOrderedPosition1();
				final Position position2 = pathData.getOrderedPosition2();
				if (getIsOnRoute()) {
					Data.put(vehiclePositions, position1, position2, vehiclePosition -> {
						final VehiclePosition newVehiclePosition = vehiclePosition == null ? new VehiclePosition() : vehiclePosition;
						newVehiclePosition.addSegment(blockedBounds.leftDouble(), blockedBounds.rightDouble(), id);
						return newVehiclePosition;
					}, Object2ObjectAVLTreeMap::new);
				}
				minMaxPositions[0] = Position.getMin(minMaxPositions[0], Position.getMin(position1, position2));
				minMaxPositions[1] = Position.getMax(minMaxPositions[1], Position.getMax(position1, position2));
			}

			index--;
		}

		if (siding != null) {
			if (siding.area != null && data instanceof Simulator) {
				final double updateRadius = ((Simulator) data).clientGroup.getUpdateRadius();
				final boolean needsUpdate = vehicleExtraData.checkForUpdate();
				final int pathUpdateIndex = Math.max(0, index + 1);
				((Simulator) data).clientGroup.iterateClients(client -> {
					final Position position = client.getPosition();
					if ((minMaxPositions[0] == null || minMaxPositions[1] == null) ? siding.area.inArea(position, updateRadius) : Utilities.isBetween(position, minMaxPositions[0], minMaxPositions[1], updateRadius)) {
						client.update(this, needsUpdate, pathUpdateIndex);
					}
				});
			}

			vehicleExtraData.setRoutePlatformInfo(siding.area, currentIndex);
		}
	}

	/**
	 * @return the distance until the rail is blocked or -1 if there is nothing in front
	 */
	private double railBlockedDistance(int currentIndex, double checkRailProgress, double checkDistance, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions) {
		if (vehiclePositions == null) {
			return -1;
		}

		int index = currentIndex;
		while (true) {
			final PathData pathData = vehicleExtraData.immutablePath.get(index);

			if (pathData.isSignalBlocked(id)) {
				return Math.max(0, pathData.getStartDistance() - railProgress);
			} else if (Utilities.isIntersecting(pathData.getStartDistance(), pathData.getEndDistance(), checkRailProgress, checkRailProgress + checkDistance + transportMode.stoppingSpace)) {
				final DoubleDoubleImmutablePair blockedBounds = getBlockedBounds(pathData, checkRailProgress, checkRailProgress + checkDistance + transportMode.stoppingSpace);
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = Data.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						final double overlap = vehiclePosition.getOverlap(blockedBounds.leftDouble(), blockedBounds.rightDouble(), id);
						if (overlap >= 0) {
							// If vehicle is stopped, require at least 2 more blocks of clear track before proceeding
							return Math.max(0, checkDistance - overlap - (speed == 0 ? 2 : 0));
						}
					}
				}
			}

			index++;

			if (index >= vehicleExtraData.immutablePath.size()) {
				return -1;
			}
		}
	}

	@Nullable
	private Vector getPosition(double value) {
		final PathData pathData = Utilities.getElement(vehicleExtraData.immutablePath, Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, value));
		return pathData == null ? null : pathData.getPosition(value - pathData.getStartDistance());
	}

	@Nullable
	private ObjectObjectImmutablePair<Vector, Vector> getBogiePositions(double value) {
		final Vector position1 = getPosition(value - 1);
		final Vector position2 = getPosition(value + 1);
		return position1 == null || position2 == null ? null : new ObjectObjectImmutablePair<>(position1, position2);
	}

	private static DoubleDoubleImmutablePair getBlockedBounds(PathData pathData, double lowerRailProgress, double upperRailProgress) {
		final double distanceFromStart = Utilities.clamp(lowerRailProgress, pathData.getStartDistance(), pathData.getEndDistance()) - pathData.getStartDistance();
		final double distanceToEnd = pathData.getEndDistance() - Utilities.clamp(upperRailProgress, pathData.getStartDistance(), pathData.getEndDistance());
		return new DoubleDoubleImmutablePair(pathData.reversePositions ? distanceToEnd : distanceFromStart, pathData.getEndDistance() - pathData.getStartDistance() - (pathData.reversePositions ? distanceFromStart : distanceToEnd));
	}
}
