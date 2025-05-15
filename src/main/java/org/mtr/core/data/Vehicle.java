package org.mtr.core.data;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.Main;
import org.mtr.core.generated.data.VehicleSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;

import javax.annotation.Nullable;
import java.util.UUID;

public class Vehicle extends VehicleSchema implements Utilities {

	/**
	 * The amount of time to check for a blocked status again after detecting a blocked status.
	 */
	private long stoppingCooldown;
	private long deviation;
	private double deviationSpeedAdjustment;
	/**
	 * The time until the vehicle switches from manual to automatic
	 */
	private long manualCooldown;
	private long doorCooldown;
	private boolean atoOverride;

	public final VehicleExtraData vehicleExtraData;
	private final Siding siding;
	/**
	 * If a vehicle is clientside, don't open the doors or start up automatically. Always wait for a socket update instead.
	 */
	private final boolean isClientside;

	public static final int MAX_POWER_LEVEL = 7;
	public static final int POWER_LEVEL_RATIO = 5;
	public static final int DOOR_MOVE_TIME = 3200;
	private static final int DOOR_DELAY = 1000;

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, TransportMode transportMode, Data data) {
		super(transportMode, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		this.isClientside = !(data instanceof Simulator);
	}

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, ReaderBase readerBase, Data data) {
		super(readerBase, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
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

	public void initVehiclePositions(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> vehiclePositions) {
		writeVehiclePositions(Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, railProgress), vehiclePositions);
	}

	public void simulate(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, @Nullable Long2LongAVLTreeMap vehicleTimesAlongRoute) {
		final int currentIndex;
		manualCooldown = vehicleExtraData.getIsManualAllowed() && vehicleExtraData.containsDriver() ? vehicleExtraData.getManualToAutomaticTime() : Math.max(0, manualCooldown - millisElapsed);
		doorCooldown = vehicleExtraData.getDoorMultiplier() > 0 ? DOOR_MOVE_TIME + DOOR_DELAY : Math.max(0, doorCooldown - millisElapsed);

		if (getIsOnRoute()) {
			if (vehicleExtraData.getRepeatIndex2() == 0 && railProgress >= vehicleExtraData.getTotalDistance() - (vehicleExtraData.getRailLength() - vehicleExtraData.getTotalVehicleLength()) / 2) {
				// If the route does not repeat infinitely and the vehicle is reaching the end
				currentIndex = 0;
				if (!isClientside) {
					vehicleExtraData.setPowerLevel(Math.min(vehicleExtraData.getPowerLevel(), -1));
				}
				simulateInDepot();
			} else {
				// If the vehicle is on route normally
				currentIndex = Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, railProgress);
				if (speed <= 0) {
					// If the vehicle is stopped (at a platform or waiting for a signal)
					speed = 0;
					simulateStopped(millisElapsed, vehiclePositions, currentIndex);
				} else {
					// If the vehicle is moving normally
					simulateMoving(millisElapsed, vehiclePositions, currentIndex);
				}
			}
		} else {
			currentIndex = 0;
			simulateInDepot();
		}

		stoppingCooldown = Math.max(0, stoppingCooldown - millisElapsed);

		if (vehiclePositions != null) {
			writeVehiclePositions(currentIndex, vehiclePositions.get(1));
		}

		if (vehicleTimesAlongRoute != null) {
			final long timeAlongRoute = getTimeAlongRoute(railProgress);
			if (timeAlongRoute > 0) {
				vehicleTimesAlongRoute.put(departureIndex, timeAlongRoute);
			}
		}

		if (!isClientside) {
			// Remove entities that have dismounted
			if (data instanceof Simulator) {
				vehicleExtraData.removeRidingEntitiesIf(vehicleRidingEntity -> !((Simulator) data).isRiding(vehicleRidingEntity.uuid, id));
			}

			// Update the manual state for the client
			vehicleExtraData.setIsCurrentlyManual(isCurrentlyManual());
		}
	}

	public void startUp(long newDepartureIndex, long newSidingDepartureTime) {
		if (isClientside) {
			Main.LOGGER.warn("Vehicle#startUp should only be called on the server side!");
		}

		vehicleExtraData.closeDoors();

		// Ensure doors are closed before starting up
		if (doorCooldown == 0) {
			departureIndex = newDepartureIndex;
			sidingDepartureTime = newSidingDepartureTime;
			railProgress += Siding.ACCELERATION_DEFAULT;
			elapsedDwellTime = 0;
			speed = Siding.ACCELERATION_DEFAULT;
			atoOverride = false;
			vehicleExtraData.setSpeedTarget(speed);
			setNextStoppingIndex();

			// Calculate deviation speed adjustment
			updateDeviation();
			if (deviation > 0 && nextStoppingIndexAto < vehicleExtraData.immutablePath.size() - 1 && siding != null && siding.getDelayedVehicleSpeedIncreasePercentage() > 0) {
				final double endRailProgress = vehicleExtraData.immutablePath.get((int) nextStoppingIndexAto).getEndDistance();
				final double distance = endRailProgress - railProgress;
				final double scheduledDuration = getTimeAlongRoute(endRailProgress) - getTimeAlongRoute(railProgress);
				final double expectedDuration = Math.max(1, scheduledDuration - deviation);
				final double averageSpeed = distance / scheduledDuration;
				final double expectedSpeed = distance / expectedDuration;
				deviationSpeedAdjustment = Math.min(expectedSpeed / averageSpeed, siding.getDelayedVehicleSpeedIncreasePercentage() / 100F + 1);
			} else {
				deviationSpeedAdjustment = 1;
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

	public Vector getHeadPosition() {
		return getPosition(railProgress, new DoubleArrayList());
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

				if (vehicleExtraData.getIsManualAllowed() && vehicleRidingEntity.isDriver()) {
					final int powerLevel = vehicleExtraData.getPowerLevel();
					if (vehicleRidingEntity.manualToggleDoors()) {
						if (speed > 0) {
							vehicleExtraData.closeDoors();
						} else {
							vehicleExtraData.toggleDoors();
						}
					}

					if (vehicleRidingEntity.manualToggleAto()) {
						atoOverride = speed > 0 && !atoOverride;
					}

					if (vehicleRidingEntity.manualAccelerate()) {
						vehicleExtraData.setPowerLevel(Math.min(powerLevel + 1, MAX_POWER_LEVEL));
						atoOverride = false;
					} else if (vehicleRidingEntity.manualBrake()) {
						vehicleExtraData.setPowerLevel(Math.max(powerLevel - 1, -MAX_POWER_LEVEL - 1));
						atoOverride = false;
					}
				}
			});

			vehicleExtraData.removeRidingEntitiesIf(vehicleRidingEntity -> uuidToRemove.contains(vehicleRidingEntity.uuid));
			vehicleExtraData.addRidingEntities(vehicleRidingEntitiesToAdd);
		}
	}

	long getSidingDepartureTime() {
		return sidingDepartureTime;
	}

	private void simulateInDepot() {
		railProgress = vehicleExtraData.getDefaultPosition();
		reversed = false;
		speed = 0;
		nextStoppingIndexAto = 0;
		nextStoppingIndexManual = 0;
		departureIndex = -1;
		sidingDepartureTime = -1;
		vehicleExtraData.closeDoors();

		if (!isClientside && isCurrentlyManual() && vehicleExtraData.getPowerLevel() > 0) {
			startUp(-1, data.getCurrentMillis());
		}
	}

	private void simulateStopped(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex) {
		if (isClientside) {
			return;
		}

		final PathData pathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex);
		if (pathData == null) {
			return;
		}

		vehicleExtraData.setStoppingPoint(railProgress);
		stoppingCooldown = 0;

		if (isCurrentlyManual()) {
			if (railProgress == pathData.getStartDistance()) {
				// Stopped behind a node
				final PathData currentPathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex - 1);
				final PathData nextPathData = Utilities.getElement(vehicleExtraData.immutablePath, vehicleExtraData.getRepeatIndex2() > 0 && currentIndex >= vehicleExtraData.getRepeatIndex2() ? vehicleExtraData.getRepeatIndex1() : currentIndex);
				final boolean isOpposite = currentPathData != null && nextPathData != null && currentPathData.isOppositeRail(nextPathData);
				final double nextStartDistance = nextPathData == null ? 0 : nextPathData.getStartDistance() + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0);

				if (vehicleExtraData.getPowerLevel() > 0 && railBlockedDistance(currentIndex, nextStartDistance, 0, vehiclePositions, true, false) < 0) {
					if (doorCooldown == 0) {
						railProgress = nextStartDistance;
						if (isOpposite) {
							reversed = !reversed;
						}
					}
					startUp(departureIndex, sidingDepartureTime);
				}
			} else {
				// Stopped anywhere else
				if (vehicleExtraData.getPowerLevel() > 0 && railBlockedDistance(currentIndex, railProgress, 0, vehiclePositions, true, false) < 0) {
					startUp(departureIndex, sidingDepartureTime);
				}
			}
		} else {
			if (railProgress == pathData.getStartDistance()) {
				// Stopped behind a node
				final PathData currentPathData = Utilities.getElement(vehicleExtraData.immutablePath, currentIndex - 1);
				final PathData nextPathData = Utilities.getElement(vehicleExtraData.immutablePath, vehicleExtraData.getRepeatIndex2() > 0 && currentIndex >= vehicleExtraData.getRepeatIndex2() ? vehicleExtraData.getRepeatIndex1() : currentIndex);
				final boolean isOpposite = currentPathData != null && nextPathData != null && currentPathData.isOppositeRail(nextPathData);
				final double nextStartDistance = nextPathData == null ? 0 : nextPathData.getStartDistance() + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0);
				final long totalDwellMillis = currentPathData == null ? 0 : currentPathData.getDwellTime();
				final long doorCloseTime = Math.max(totalDwellMillis / 2, totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY);

				if (totalDwellMillis > 0 && elapsedDwellTime >= DOOR_DELAY && elapsedDwellTime < doorCloseTime) {
					vehicleExtraData.openDoors();
				} else if (elapsedDwellTime >= doorCloseTime && railBlockedDistance(currentIndex, nextStartDistance, 0, vehiclePositions, true, false) < 0) {
					if (doorCooldown == 0) {
						railProgress = nextStartDistance;
						if (isOpposite) {
							reversed = !reversed;
						}
					}
					startUp(departureIndex, sidingDepartureTime);
				}

				final long deviationAdjustment;
				if (siding != null && elapsedDwellTime >= DOOR_DELAY + DOOR_MOVE_TIME && elapsedDwellTime < doorCloseTime) {
					if (deviation > 0) {
						// If delayed
						deviationAdjustment = Math.min(deviation, (doorCloseTime - elapsedDwellTime) * siding.getDelayedVehicleReduceDwellTimePercentage() / 100);
					} else {
						// If early
						deviationAdjustment = siding.getEarlyVehicleIncreaseDwellTime() ? Math.max(deviation, -millisElapsed) : 0;
					}
					deviation -= deviationAdjustment;
				} else {
					deviationAdjustment = 0;
				}

				elapsedDwellTime = Math.min(elapsedDwellTime + millisElapsed + deviationAdjustment, totalDwellMillis);
			} else {
				// Stopped anywhere else
				if (railBlockedDistance(currentIndex, railProgress, 0, vehiclePositions, true, false) < 0) {
					startUp(departureIndex, sidingDepartureTime);
				}
			}
		}
	}

	private void simulateMoving(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex) {
		// Tracks the distance
		final double stoppingPoint;
		// Tracks the speed
		final double speedTarget;
		// Tracks the acceleration
		final int powerLevel;

		if (isClientside) {
			stoppingPoint = vehicleExtraData.getStoppingPoint();
			speedTarget = vehicleExtraData.getSpeedTarget();
			powerLevel = vehicleExtraData.getPowerLevel();
		} else {
			final double safeStoppingDistance = 0.5 * speed * speed / vehicleExtraData.getDeceleration() * (isCurrentlyManual() ? POWER_LEVEL_RATIO : 1); // when on manual mode, check for blocked rails on the lowest deceleration (B1)
			final double hardStoppingDistance = 0.5 * speed * speed / (Siding.MAX_ACCELERATION * 2);

			// Set the stopping point
			if (transportMode.continuousMovement) {
				stoppingPoint = Double.MAX_VALUE;
				if (vehicleExtraData.immutablePath.get(currentIndex).getDwellTime() > 0) {
					vehicleExtraData.openDoors();
				} else {
					vehicleExtraData.closeDoors();
				}
			} else {
				final double pathStoppingPoint = getPathStoppingPoint();
				if (stoppingCooldown > 0) {
					stoppingPoint = Math.min(vehicleExtraData.getStoppingPoint(), pathStoppingPoint);
				} else {
					final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions, true, false);
					if (railBlockedDistance < 0) {
						stoppingPoint = pathStoppingPoint;
					} else {
						// Set the stopping point to the blocked position
						stoppingPoint = Math.min(railBlockedDistance + railProgress, pathStoppingPoint);
						stoppingCooldown = 1000;
					}
				}
			}

			// Set the power level and speed target
			if (stoppingPoint - railProgress < (isCurrentlyManual() ? hardStoppingDistance : safeStoppingDistance)) {
				// If blocked ahead, slow down (using normal deceleration for automatic and emergency brake for manual)
				speedTarget = -1;
				powerLevel = Math.min(vehicleExtraData.getPowerLevel(), isCurrentlyManual() && hardStoppingDistance > 2 ? -MAX_POWER_LEVEL - 1 : -POWER_LEVEL_RATIO);
				atoOverride = true;
			} else {
				if (isCurrentlyManual()) {
					if (speed > vehicleExtraData.getMaxManualSpeed()) {
						// Slow down if above the max manual speed
						speedTarget = vehicleExtraData.getMaxManualSpeed();
						powerLevel = -POWER_LEVEL_RATIO;
					} else {
						powerLevel = vehicleExtraData.getPowerLevel();
						speedTarget = powerLevel > 0 ? vehicleExtraData.getMaxManualSpeed() : powerLevel < 0 ? 0 : speed;
					}
				} else {
					final double upcomingSlowerSpeed = Siding.getUpcomingSlowerSpeed(vehicleExtraData.immutablePath, currentIndex, railProgress, speed, vehicleExtraData.getDeceleration());
					if (upcomingSlowerSpeed >= 0 && upcomingSlowerSpeed < speed) {
						speedTarget = upcomingSlowerSpeed * deviationSpeedAdjustment;
						powerLevel = -POWER_LEVEL_RATIO;
					} else {
						speedTarget = vehicleExtraData.immutablePath.get(currentIndex).getSpeedLimitMetersPerMillisecond() * deviationSpeedAdjustment;
						powerLevel = Double.compare(speedTarget, speed) * POWER_LEVEL_RATIO;
					}
				}
			}

			// Sync to the client
			vehicleExtraData.setStoppingPoint(stoppingPoint);
			vehicleExtraData.setSpeedTarget(speedTarget);
			vehicleExtraData.setPowerLevel(powerLevel);
		}

		// Set speed
		if (speedTarget < 0) {
			final double stoppingDistance = stoppingPoint - railProgress;
			speed = stoppingDistance <= 0 ? Siding.ACCELERATION_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, Siding.ACCELERATION_DEFAULT);
		} else {
			if (powerLevel > 0) {
				speed = Math.min(speed + vehicleExtraData.getAcceleration() * powerLevel / POWER_LEVEL_RATIO * millisElapsed, speedTarget);
			} else if (powerLevel < 0) {
				speed = Math.max(speed + (powerLevel < -MAX_POWER_LEVEL ? -Siding.MAX_ACCELERATION * 2 : vehicleExtraData.getDeceleration() * powerLevel / POWER_LEVEL_RATIO) * millisElapsed, speedTarget);
			} else {
				speed = speedTarget;
			}
		}

		// Set rail progress
		railProgress += speed * millisElapsed;
		if (railProgress >= stoppingPoint) {
			railProgress = stoppingPoint;
			speed = 0;
			vehicleExtraData.setSpeedTarget(0);
			updateDeviation();
			if (!isClientside) {
				atoOverride = false;
				vehicleExtraData.setPowerLevel(Math.min(vehicleExtraData.getPowerLevel(), -1));
			}
		} else if (vehicleExtraData.getRepeatIndex2() > 0 && railProgress >= vehicleExtraData.getTotalDistance()) {
			railProgress = vehicleExtraData.immutablePath.get(vehicleExtraData.getRepeatIndex1()).getStartDistance() + railProgress - vehicleExtraData.getTotalDistance();
		}
	}

	/**
	 * Gets the stopping point of the path (a platform, a turnback, or the end of the route).
	 *
	 * @return the position (not the index) of the stop
	 */
	private double getPathStoppingPoint() {
		final double stoppingPointByStoppingIndex;
		final PathData pathDataAto = Utilities.getElement(vehicleExtraData.immutablePath, (int) nextStoppingIndexAto);
		final boolean pastAtoStoppingPoint = pathDataAto != null && railProgress > pathDataAto.getEndDistance();
		final int nextStoppingIndex = (int) (isCurrentlyManual() || pastAtoStoppingPoint ? nextStoppingIndexManual : nextStoppingIndexAto);

		if (nextStoppingIndex >= vehicleExtraData.immutablePath.size() - 1) {
			// Set the stopping point to the end of the whole journey
			stoppingPointByStoppingIndex = vehicleExtraData.getTotalDistance() - (vehicleExtraData.getRepeatIndex2() > 0 ? 0 : (vehicleExtraData.getRailLength() - vehicleExtraData.getTotalVehicleLength()) / 2);
		} else {
			// Set the stopping point to the next expected platform or turnback
			stoppingPointByStoppingIndex = vehicleExtraData.immutablePath.get(nextStoppingIndex).getEndDistance();
		}

		if (pastAtoStoppingPoint) {
			setNextStoppingIndex();
		}

		return stoppingPointByStoppingIndex;
	}

	private boolean isCurrentlyManual() {
		if (isClientside) {
			Main.LOGGER.warn("Vehicle#isCurrentlyManual should only be called on the server side!");
		}
		return !atoOverride && manualCooldown > 0;
	}

	private void setNextStoppingIndex() {
		nextStoppingIndexAto = nextStoppingIndexManual = vehicleExtraData.immutablePath.size() - 1;
		vehicleExtraData.setStoppingPoint(vehicleExtraData.getTotalDistance());
		for (int i = Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, railProgress); i < vehicleExtraData.immutablePath.size(); i++) {
			final PathData pathData = vehicleExtraData.immutablePath.get(i);
			if (pathData.getDwellTime() > 0) {
				if (vehicleExtraData.getIsManualAllowed()) {
					nextStoppingIndexAto = Math.min(nextStoppingIndexAto, i);
					// Find the next turnback
					if (i < vehicleExtraData.immutablePath.size() - 1 && vehicleExtraData.immutablePath.get(i + 1).isOppositeRail(pathData)) {
						nextStoppingIndexManual = i;
						vehicleExtraData.setStoppingPoint(pathData.getEndDistance());
						break;
					}
				} else {
					nextStoppingIndexAto = nextStoppingIndexManual = i;
					vehicleExtraData.setStoppingPoint(pathData.getEndDistance());
					break;
				}
			}
		}
	}

	/**
	 * Indicate which portions of each path segment are occupied by this vehicle.
	 * Also check if the vehicle needs to send a socket update:
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
						pathData.isSignalBlocked(id, Rail.BlockReservation.CURRENTLY_RESERVE);
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
				((Simulator) data).clients.forEach(client -> {
					final Position position = client.getPosition();
					final double updateRadius = client.getUpdateRadius();
					if ((minMaxPositions[0] == null || minMaxPositions[1] == null) ? siding.area.inArea(position, updateRadius) : Utilities.isBetween(position, minMaxPositions[0], minMaxPositions[1], updateRadius) || !closeToDepot() && vehicleExtraData.hasRidingEntity(client.uuid)) {
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

			final double blockedStartOffset = Math.max(0, pathData.getStartDistance() - checkRailProgress);

			if (checkAndBlockSignal(index, vehiclePositions, reserveRail, secondPass)) {
				return blockedStartOffset;
			} else if (Utilities.isIntersecting(pathData.getStartDistance(), pathData.getEndDistance(), checkRailProgress, checkRailProgressEnd)) {
				final DoubleDoubleImmutablePair blockedBounds = getBlockedBounds(pathData, checkRailProgress, checkRailProgressEnd);
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = Data.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						final double closestOverlap = vehiclePosition.getClosestOverlap(blockedBounds.leftDouble(), blockedBounds.rightDouble(), pathData.reversePositions, id);
						if (closestOverlap >= 0) {
							return Math.max(0, blockedStartOffset + closestOverlap - transportMode.stoppingSpace);
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
			return firstPathData.isSignalBlocked(id, Rail.BlockReservation.DO_NOT_RESERVE);
		} else {
			final IntAVLTreeSet signalColors = firstPathData.getSignalColors();
			int index = currentIndex + 1;

			while (!signalColors.isEmpty() && index < vehicleExtraData.immutablePath.size()) {
				final PathData pathData = vehicleExtraData.immutablePath.get(index);

				if (pathData.getSignalColors().intStream().noneMatch(signalColors::contains)) {
					// Only reserve the signal block after checking if the path after the signal block is clear, not before!
					final double railBlockedDistance = railBlockedDistance(index, pathData.getStartDistance(), vehicleExtraData.getTotalVehicleLength(), vehiclePositions, false, true);
					return railBlockedDistance >= 0 && railBlockedDistance < vehicleExtraData.getTotalVehicleLength() || firstPathData.isSignalBlocked(id, reserveRail ? Rail.BlockReservation.PRE_RESERVE : Rail.BlockReservation.DO_NOT_RESERVE);
				}

				index++;
			}

			return false;
		}
	}

	private long getTimeAlongRoute(double checkRailProgress) {
		// Subtract 1 from railProgress for rounding errors
		return siding == null ? 0 : (long) Math.floor(siding.getTimeAlongRoute(checkRailProgress - (speed == 0 ? 1 : 0)) + elapsedDwellTime);
	}

	private void updateDeviation() {
		deviation = transportMode.continuousMovement || siding == null ? 0 : Utilities.circularDifference(data.getCurrentMillis() - sidingDepartureTime, getTimeAlongRoute(railProgress), siding.getRepeatInterval(MILLIS_PER_DAY));
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
