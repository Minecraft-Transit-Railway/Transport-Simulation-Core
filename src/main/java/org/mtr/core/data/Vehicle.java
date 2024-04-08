package org.mtr.core.data;

import org.mtr.core.generated.data.VehicleSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import javax.annotation.Nullable;
import java.util.UUID;

public class Vehicle extends VehicleSchema {

	/**
	 * The amount of time to start up a vehicle again if blocked by a signal or another vehicle in front.
	 */
	private long stoppingCoolDown;
	private int manualNotch;

	public final VehicleExtraData vehicleExtraData;
	private final Siding siding;
	/**
	 * If a vehicle is clientside, don't open the doors or start up automatically. Always wait for a socket update instead.
	 */
	private final boolean isClientside;

	public static final int DOOR_MOVE_TIME = 3200;
	private static final int DOOR_DELAY = 1000;

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, TransportMode transportMode, Data data) {
		super(transportMode, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		isCurrentlyManual = vehicleExtraData.getIsManualAllowed();
		this.isClientside = !(data instanceof Simulator);
	}

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, ReaderBase readerBase, Data data) {
		super(readerBase, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		isCurrentlyManual = vehicleExtraData.getIsManualAllowed();
		this.isClientside = !(data instanceof Simulator);
		updateData(readerBase);
	}

	/**
	 * @deprecated for {@link org.mtr.core.operation.VehicleUpdate} use only
	 */
	@Deprecated
	public Vehicle(ReaderBase readerBase) {
		this(new VehicleExtraData(readerBase), null, readerBase, new ClientData());
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public boolean isMoving() {
		return speed != 0;
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
				manualNotch = 0;
				simulateInDepot();
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

		stoppingCoolDown = Math.max(0, stoppingCoolDown - millisElapsed);

		if (vehiclePositions != null) {
			writeVehiclePositions(currentIndex, vehiclePositions.get(1));
		}

		if (siding != null && vehicleTimesAlongRoute != null) {
			// Subtract 1 from railProgress for rounding errors
			vehicleTimesAlongRoute.put(departureIndex, (long) Math.floor(siding.getTimeAlongRoute(railProgress - 1) + elapsedDwellTime));
		}

		if (!isClientside && data instanceof Simulator) {
			vehicleExtraData.removeRidingEntitiesIf(vehicleRidingEntity -> !((Simulator) data).isRiding(vehicleRidingEntity.uuid, id));
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

	public ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> getVehicleCarsAndPositions() {
		final ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> vehicleCarsAndPositions = new ObjectArrayList<>();
		double checkRailProgress = railProgress - (reversed ? vehicleExtraData.getTotalVehicleLength() : 0);

		for (int i = 0; i < vehicleExtraData.immutableVehicleCars.size(); i++) {
			final VehicleCar vehicleCar = vehicleExtraData.immutableVehicleCars.get(i);
			checkRailProgress += (reversed ? 1 : -1) * vehicleCar.getCouplingPadding1(i == 0);
			final double halfLength = vehicleCar.getLength() / 2;
			final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogiePositionsList = new ObjectArrayList<>();
			final DoubleArrayList overrideY = new DoubleArrayList(); // For airplanes, don't nosedive when descending
			bogiePositionsList.add(getBogiePositions(checkRailProgress + (reversed ? 1 : -1) * (halfLength + vehicleCar.getBogie1Position()), overrideY));

			if (!vehicleCar.hasOneBogie) {
				bogiePositionsList.add(getBogiePositions(checkRailProgress + (reversed ? 1 : -1) * (halfLength + vehicleCar.getBogie2Position()), overrideY));
			}

			vehicleCarsAndPositions.add(new ObjectObjectImmutablePair<>(vehicleCar, bogiePositionsList));
			checkRailProgress += (reversed ? 1 : -1) * vehicleCar.getTotalLength(true, false);
		}

		return vehicleCarsAndPositions;
	}

	void updateRidingEntities(ObjectArrayList<VehicleRidingEntity> vehicleRidingEntities) {
		if (!isClientside && data instanceof Simulator) {
			final ObjectOpenHashSet<UUID> uuidToRemove = new ObjectOpenHashSet<>();
			final ObjectOpenHashSet<VehicleRidingEntity> vehicleRidingEntitiesToAdd = new ObjectOpenHashSet<>();
			vehicleRidingEntities.forEach(vehicleRidingEntity -> {
				uuidToRemove.add(vehicleRidingEntity.uuid);
				if (vehicleRidingEntity.isOnVehicle()) {
					vehicleRidingEntitiesToAdd.add(vehicleRidingEntity);
					((Simulator) data).ride(vehicleRidingEntity.uuid, id);
				} else {
					((Simulator) data).stopRiding(vehicleRidingEntity.uuid);
				}
			});
			vehicleExtraData.removeRidingEntitiesIf(vehicleRidingEntity -> uuidToRemove.contains(vehicleRidingEntity.uuid));
			vehicleExtraData.addRidingEntities(vehicleRidingEntitiesToAdd);
		}
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

		final PathData pathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex);
		if (pathData == null) {
			return;
		}

		vehicleExtraData.setStoppingPoint(railProgress);
		stoppingCoolDown = 0;

		if (railProgress == pathData.getStartDistance()) {
			// Stopped behind a node
			final PathData currentPathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex - 1);
			final PathData nextPathData = Utilities.getElement(vehicleExtraData.immutablePath, vehicleExtraData.getRepeatIndex2() > 0 && currentIndex >= vehicleExtraData.getRepeatIndex2() ? vehicleExtraData.getRepeatIndex1() : currentIndex);
			final boolean isOpposite = currentPathData != null && nextPathData != null && currentPathData.isOppositeRail(nextPathData);
			final double nextStartDistance = nextPathData == null ? 0 : nextPathData.getStartDistance();
			final long totalDwellMillis = currentPathData == null ? 0 : currentPathData.getDwellTime();
			final long doorCloseTime = Math.max(totalDwellMillis / 2, totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY);
			final boolean railClear = railBlockedDistance(currentIndex, nextStartDistance + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0), 0, vehiclePositions, elapsedDwellTime >= doorCloseTime, false) < 0;

			if (Utilities.isBetween(elapsedDwellTime, DOOR_DELAY, doorCloseTime)) {
				vehicleExtraData.openDoors();
			} else {
				vehicleExtraData.closeDoors();
			}

			if (elapsedDwellTime + millisElapsed < doorCloseTime || railClear) {
				elapsedDwellTime += millisElapsed;
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
		} else {
			// Stopped anywhere else
			if (railBlockedDistance(currentIndex, railProgress, 0, vehiclePositions, true, false) < 0) {
				startUp(departureIndex);
			}
		}
	}

	private void simulateAutomaticMoving(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex) {
		final double newAcceleration = vehicleExtraData.getAcceleration() * millisElapsed;
		final double newDeceleration = vehicleExtraData.getDeceleration() * millisElapsed;
		final double safeStoppingDistance = 0.5 * speed * speed / vehicleExtraData.getDeceleration();
		final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions, true, false);
		final double stoppingPoint;

		if (transportMode.continuousMovement) {
			stoppingPoint = Double.MAX_VALUE;
			if (vehicleExtraData.immutablePath.get(currentIndex).getDwellTime() > 0) {
				vehicleExtraData.openDoors();
			} else {
				vehicleExtraData.closeDoors();
			}
		} else if (isClientside || stoppingCoolDown > 0) {
			stoppingPoint = vehicleExtraData.getStoppingPoint();
		} else if (railBlockedDistance < 0) {
			if (nextStoppingIndex >= vehicleExtraData.immutablePath.size() - 1) {
				stoppingPoint = vehicleExtraData.getTotalDistance() - (vehicleExtraData.getRailLength() - vehicleExtraData.getTotalVehicleLength()) / 2;
			} else {
				stoppingPoint = vehicleExtraData.immutablePath.get((int) nextStoppingIndex).getEndDistance();
			}
		} else {
			stoppingPoint = railBlockedDistance + railProgress;
			stoppingCoolDown = 1000;
		}

		vehicleExtraData.setStoppingPoint(stoppingPoint);
		final double stoppingDistance = stoppingPoint - railProgress;

		if (stoppingDistance < safeStoppingDistance) {
			speed = stoppingDistance <= 0 ? Siding.BRAKING_POWER_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, Siding.BRAKING_POWER_DEFAULT);
		} else {
			final double railSpeed = getRailSpeed(currentIndex);
			if (speed < railSpeed) {
				speed = Math.min(speed + newAcceleration, railSpeed);
			} else if (speed > railSpeed) {
				speed = Math.max(speed - newDeceleration, railSpeed);
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
			// TODO maybe use previous rail speed as the speed limit
			railSpeed = Math.max(transportMode.defaultSpeedMetersPerMillisecond, speed);
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
			final Position position1 = pathData.getOrderedPosition1();
			final Position position2 = pathData.getOrderedPosition2();
			minMaxPositions[0] = Position.getMin(minMaxPositions[0], Position.getMin(position1, position2));
			minMaxPositions[1] = Position.getMax(minMaxPositions[1], Position.getMax(position1, position2));

			if (railProgress - vehicleExtraData.getTotalVehicleLength() > pathData.getEndDistance()) {
				break;
			}

			if (!transportMode.continuousMovement) {
				final DoubleDoubleImmutablePair blockedBounds = getBlockedBounds(pathData, railProgress - vehicleExtraData.getTotalVehicleLength(), railProgress - 0.01);
				if (blockedBounds.rightDouble() - blockedBounds.leftDouble() > 0.01) {
					if (getIsOnRoute() && index > 0) {
						Data.put(vehiclePositions, position1, position2, vehiclePosition -> {
							final VehiclePosition newVehiclePosition = vehiclePosition == null ? new VehiclePosition() : vehiclePosition;
							newVehiclePosition.addSegment(blockedBounds.leftDouble(), blockedBounds.rightDouble(), id);
							return newVehiclePosition;
						}, Object2ObjectAVLTreeMap::new);
						pathData.isSignalBlocked(id, true);
					}
				}
			}

			index--;
		}

		if (siding != null) {
			if (siding.area != null && data instanceof Simulator) {
				final boolean needsUpdate = vehicleExtraData.checkForUpdate();
				// TODO for continuous movement, maybe only send the path once rather than sending the entire path for each vehicle
				final int pathUpdateIndex = transportMode.continuousMovement ? 0 : Math.max(0, index + 1);
				((Simulator) data).clients.values().forEach(client -> {
					final Position position = client.getPosition();
					final double updateRadius = client.getUpdateRadius();
					if ((minMaxPositions[0] == null || minMaxPositions[1] == null) ? siding.area.inArea(position, updateRadius) : Utilities.isBetween(position, minMaxPositions[0], minMaxPositions[1], updateRadius)) {
						client.update(this, needsUpdate, pathUpdateIndex);
					}
				});
			}

			vehicleExtraData.setRoutePlatformInfo(siding.area, currentIndex);
		}
	}

	/**
	 * Checks if the rails ahead are clear up to a certain point (in terms of other vehicles or signals).
	 *
	 * @return the distance until the rail is blocked or -1 if there is nothing in front
	 */
	private double railBlockedDistance(int currentIndex, double checkRailProgress, double checkDistance, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, boolean reserveRail, boolean secondPass) {
		int index = currentIndex;

		while (vehiclePositions != null && index < vehicleExtraData.immutablePath.size()) {
			final PathData pathData = vehicleExtraData.immutablePath.get(index);
			final double checkRailProgressEnd = checkRailProgress + checkDistance + transportMode.stoppingSpace;

			if (pathData.getStartDistance() >= checkRailProgressEnd) {
				return -1;
			}

			if (checkAndBlockSignal(index, vehiclePositions, reserveRail, secondPass)) {
				return Math.max(0, pathData.getStartDistance() - checkRailProgress);
			} else if (Utilities.isIntersecting(pathData.getStartDistance(), pathData.getEndDistance(), checkRailProgress, checkRailProgressEnd)) {
				final DoubleDoubleImmutablePair blockedBounds = getBlockedBounds(pathData, checkRailProgress, checkRailProgressEnd);
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = Data.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						final double overlap = vehiclePosition.getOverlap(blockedBounds.leftDouble(), blockedBounds.rightDouble(), id);
						if (overlap >= 0) {
							return Math.max(0, checkDistance - overlap);
						}
					}
				}
			}

			index++;
		}

		return -1;
	}

	/**
	 * If a signal block is encountered, first check if the path after the entire block is clear. If so, reserve the signal block.
	 *
	 * @return if the vehicle should stop
	 */
	private boolean checkAndBlockSignal(int currentIndex, ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, boolean reserveRail, boolean secondPass) {
		final PathData firstPathData = vehicleExtraData.immutablePath.get(currentIndex);

		if (secondPass) {
			return firstPathData.isSignalBlocked(id, false);
		} else {
			final IntAVLTreeSet signalColors = firstPathData.getSignalColors();
			int index = currentIndex + 1;

			while (!signalColors.isEmpty() && index < vehicleExtraData.immutablePath.size()) {
				final PathData pathData = vehicleExtraData.immutablePath.get(index);

				if (pathData.getSignalColors().intStream().noneMatch(signalColors::contains)) {
					// Only reserve the signal block after checking if the path after the signal block is clear, not before!
					final double railBlockedDistance = railBlockedDistance(index, pathData.getStartDistance(), vehicleExtraData.getTotalVehicleLength(), vehiclePositions, false, true);
					return railBlockedDistance >= 0 && railBlockedDistance < vehicleExtraData.getTotalVehicleLength() || firstPathData.isSignalBlocked(id, reserveRail);
				}

				index++;
			}

			return false;
		}
	}

	@Nullable
	private Vector getPosition(double value, DoubleArrayList overrideY) {
		final PathData pathData = Utilities.getElement(vehicleExtraData.immutablePath, Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, value));
		if (pathData == null) {
			return null;
		} else {
			final Vector vector = pathData.getPosition(value - pathData.getStartDistance());
			if (transportMode == TransportMode.AIRPLANE && pathData.getSpeedLimitKilometersPerHour() == 900 && pathData.isDescending()) {
				if (overrideY.isEmpty()) {
					overrideY.add(vector.y);
					return vector;
				} else {
					return new Vector(vector.x, overrideY.getDouble(0), vector.z);
				}
			} else {
				return vector;
			}
		}
	}

	private ObjectObjectImmutablePair<Vector, Vector> getBogiePositions(double value, DoubleArrayList overrideY) {
		final double lowerBound = railProgress - vehicleExtraData.getTotalVehicleLength();
		final double clampedValue = Utilities.clamp(value, lowerBound, railProgress);
		final double value1;
		final double value2;
		final double clamp = Utilities.clamp(Math.min(Math.abs(clampedValue - lowerBound), Math.abs(clampedValue - railProgress)), 0.1, 1);
		value1 = Utilities.clamp(clampedValue + (reversed ? -clamp : clamp), lowerBound, railProgress);
		value2 = Utilities.clamp(clampedValue - (reversed ? -clamp : clamp), lowerBound, railProgress);
		final Vector position1 = getPosition(value1, overrideY);
		final Vector position2 = getPosition(value2, overrideY);
		return position1 == null || position2 == null ? new ObjectObjectImmutablePair<>(new Vector(value1, 0, 0), new Vector(value2, 0, 0)) : new ObjectObjectImmutablePair<>(position1, position2);
	}

	private static DoubleDoubleImmutablePair getBlockedBounds(PathData pathData, double lowerRailProgress, double upperRailProgress) {
		final double distanceFromStart = Utilities.clamp(lowerRailProgress, pathData.getStartDistance(), pathData.getEndDistance()) - pathData.getStartDistance();
		final double distanceToEnd = pathData.getEndDistance() - Utilities.clamp(upperRailProgress, pathData.getStartDistance(), pathData.getEndDistance());
		return new DoubleDoubleImmutablePair(pathData.reversePositions ? distanceToEnd : distanceFromStart, pathData.getEndDistance() - pathData.getStartDistance() - (pathData.reversePositions ? distanceFromStart : distanceToEnd));
	}
}
