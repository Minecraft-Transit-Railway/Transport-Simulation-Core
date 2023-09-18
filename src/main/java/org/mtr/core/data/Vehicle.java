package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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

	public static final int DOOR_MOVE_TIME = 64;
	private static final int DOOR_DELAY = 20;

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, TransportMode transportMode, Data data) {
		super(transportMode, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		isCurrentlyManual = vehicleExtraData.getIsManualAllowed();
	}

	public Vehicle(VehicleExtraData vehicleExtraData, @Nullable Siding siding, ReaderBase readerBase, Data data) {
		super(readerBase, data);
		this.siding = siding;
		this.vehicleExtraData = vehicleExtraData;
		isCurrentlyManual = vehicleExtraData.getIsManualAllowed();
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
		writeVehiclePositions(Utilities.getIndexFromConditionalList(vehicleExtraData.newPath, railProgress), vehiclePositions);
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
				currentIndex = Utilities.getIndexFromConditionalList(vehicleExtraData.newPath, railProgress);
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

	public ObjectArrayList<Vector> getPositions() {
		final ObjectArrayList<Vector> positions = new ObjectArrayList<>();
		double railProgressOffset = 0;
		for (int i = 0; i <= vehicleExtraData.newVehicleCars.size(); i++) {
			final int currentIndex = Utilities.getIndexFromConditionalList(vehicleExtraData.newPath, railProgress - railProgressOffset);
			final PathData pathData = vehicleExtraData.newPath.get(currentIndex);
			positions.add(pathData.getRail().getPosition(railProgress - railProgressOffset - pathData.getStartDistance()));
			railProgressOffset += i < vehicleExtraData.newVehicleCars.size() ? vehicleExtraData.newVehicleCars.get(i).getLength() : 0;
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
		final PathData currentPathData = Utilities.getElement(vehicleExtraData.newPath, currentIndex - 1);
		final PathData nextPathData = Utilities.getElement(vehicleExtraData.newPath, vehicleExtraData.getRepeatIndex2() > 0 && currentIndex >= vehicleExtraData.getRepeatIndex2() ? vehicleExtraData.getRepeatIndex1() : currentIndex);
		final boolean isOpposite = currentPathData != null && nextPathData != null && currentPathData.isOppositeRail(nextPathData);
		final double nextStartDistance = nextPathData == null ? 0 : nextPathData.getStartDistance();
		final boolean railClear = railBlockedDistance(currentIndex, nextStartDistance + (isOpposite ? vehicleExtraData.getTotalVehicleLength() : 0), 0, vehiclePositions) < 0;
		final long totalDwellMillis = currentPathData == null ? 0 : currentPathData.getDwellTime();

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
			railProgress = nextStartDistance;
			if (isOpposite) {
				railProgress += vehicleExtraData.getTotalVehicleLength();
				reversed = !reversed;
			}
			startUp(departureIndex);
		}
	}

	private void simulateAutomaticMoving(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex) {
		final double newAcceleration = vehicleExtraData.getAcceleration() * millisElapsed;
		final double safeStoppingDistance = 0.5 * speed * speed / vehicleExtraData.getAcceleration();
		final double railBlockedDistance = railBlockedDistance(currentIndex, railProgress, safeStoppingDistance, vehiclePositions);
		final double stoppingPoint;

		if (railBlockedDistance < 0) {
			stoppingPoint = vehicleExtraData.newPath.get(Math.min((int) nextStoppingIndex, vehicleExtraData.newPath.size() - 1)).getEndDistance();
		} else {
			stoppingPoint = railBlockedDistance + railProgress;
		}

		vehicleExtraData.setStoppingPoint(stoppingPoint);
		final double stoppingDistance = stoppingPoint - railProgress;

		if (stoppingDistance < safeStoppingDistance) {
			speed = stoppingDistance <= 0 ? Siding.ACCELERATION_DEFAULT : Math.max(speed - (0.5 * speed * speed / stoppingDistance) * millisElapsed, Siding.ACCELERATION_DEFAULT);
		} else {
			final Rail thisRail = vehicleExtraData.newPath.get(currentIndex).getRail();
			final double railSpeed;

			if (thisRail.canAccelerate()) {
				railSpeed = thisRail.speedLimitMetersPerMillisecond;
			} else {
				final Rail lastRail = currentIndex > 0 ? vehicleExtraData.newPath.get(currentIndex - 1).getRail() : thisRail;
				railSpeed = Math.max(lastRail.canAccelerate() ? lastRail.speedLimitMetersPerMillisecond : transportMode.defaultSpeedMetersPerMillisecond, speed);
			}

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

	/**
	 * Indicate which portions of each path segment are occupied by this vehicle.
	 * Also checks if the vehicle needs to send a socket update:
	 * - Entered a client's view radius
	 * - Left a client's view radius
	 * - Started moving
	 * - New stopping index or blocked rail
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
					Data.put(vehiclePositions, position1, position2, vehiclePosition -> {
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

		if (siding != null) {
			if (siding.area != null && data instanceof Simulator) {
				final double updateRadius = ((Simulator) data).clientGroup.getUpdateRadius();
				final boolean needsUpdate = vehicleExtraData.checkForUpdate();
				((Simulator) data).clientGroup.iterateClients(client -> {
					final Position position = client.getPosition();
					if ((minMaxPositions[0] == null || minMaxPositions[1] == null) ? siding.area.inArea(position, updateRadius) : Utilities.isBetween(position, minMaxPositions[0], minMaxPositions[1], updateRadius)) {
						client.update(this, needsUpdate);
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
			final PathData pathData = vehicleExtraData.newPath.get(index);
			if (Utilities.isIntersecting(pathData.getStartDistance(), pathData.getEndDistance(), checkRailProgress, checkDistance + checkDistance)) {
				for (int i = 0; i < 2; i++) {
					final VehiclePosition vehiclePosition = Data.tryGet(vehiclePositions.get(i), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
					if (vehiclePosition != null) {
						return vehiclePosition.isBlocked(
								pathData.reversePositions ? pathData.getEndDistance() - checkRailProgress - checkDistance : checkRailProgress - pathData.getStartDistance(),
								pathData.reversePositions ? pathData.getEndDistance() - checkRailProgress : checkRailProgress + checkDistance - pathData.getStartDistance(),
								id
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
}
