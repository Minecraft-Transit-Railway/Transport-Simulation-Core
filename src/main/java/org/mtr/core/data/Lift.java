package org.mtr.core.data;

import org.mtr.core.generated.data.LiftSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.legacy.data.DataFixer;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Lift extends LiftSchema implements Utilities {

	private boolean needsUpdate;
	private Position minPosition = new Position(0, 0, 0);
	private Position maxPosition = new Position(0, 0, 0);
	private final LongArrayList distances = new LongArrayList();
	/**
	 * If a lift is clientside, don't close the doors or add instructions. Always wait for a socket update instead.
	 */
	private final boolean isClientside;

	private static final float MAX_SPEED = 10F / MILLIS_PER_SECOND; // 10 m/s
	private static final int DOOR_OPEN_TIME = 2000;
	private static final int DOOR_MOVE_TIME = Vehicle.DOOR_MOVE_TIME / 2;
	private static final int DOOR_DELAY = 500;
	private static final int STOPPING_TIME = DOOR_MOVE_TIME * 2 + DOOR_OPEN_TIME + DOOR_DELAY;

	public Lift(Data data) {
		super(TransportMode.values()[0], data);
		this.isClientside = !(data instanceof Simulator);
	}

	public Lift(ReaderBase readerBase, Data data) {
		super(DataFixer.convertLift(readerBase), data);
		this.isClientside = !(data instanceof Simulator);
		updateData(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);
		setDistances();
		needsUpdate = true;
	}

	@Override
	public boolean isValid() {
		return !floors.isEmpty();
	}

	public void tick(long millisElapsed) {
		if (stoppingCoolDown > 0) {
			stoppingCoolDown = Math.max(stoppingCoolDown - millisElapsed, 0);
			if (stoppingCoolDown == 0) {
				if (isClientside) {
					stoppingCoolDown = 1; // clientside lifts should always wait for a socket update before moving
				} else {
					needsUpdate = true;
				}
			}
		} else {
			if (instructions.isEmpty()) {
				speed = Math.max(Math.abs(speed) - Siding.ACCELERATION_DEFAULT * millisElapsed, 0) * Math.signum(speed);
			} else {
				final long nextInstructionProgress = getProgress(instructions.get(0).getFloor());

				if (speed * speed / 2 / Siding.ACCELERATION_DEFAULT > Math.abs(nextInstructionProgress - railProgress)) {
					speed = Math.max(Math.abs(speed) - Siding.ACCELERATION_DEFAULT * millisElapsed, Siding.ACCELERATION_DEFAULT) * Math.signum(speed);
				} else {
					speed = Utilities.clamp(speed + Siding.ACCELERATION_DEFAULT * millisElapsed * Math.signum(nextInstructionProgress - railProgress), -MAX_SPEED, MAX_SPEED);
				}

				if (Math.abs(railProgress - nextInstructionProgress) <= Math.abs(speed * millisElapsed)) {
					railProgress = nextInstructionProgress;
					speed = 0;
					if (!isClientside) {
						instructions.remove(0);
						stoppingCoolDown = STOPPING_TIME;
						needsUpdate = true;
					}
				}
			}

			railProgress = Utilities.clamp(railProgress + speed * millisElapsed, 0, getProgress(Integer.MAX_VALUE));
		}

		if (data instanceof Simulator) {
			((Simulator) data).clients.values().forEach(client -> {
				if (Utilities.isBetween(client.getPosition(), minPosition, maxPosition, client.getUpdateRadius())) {
					client.update(this, needsUpdate);
				}
			});

			needsUpdate = false;
		}
	}

	/**
	 * @param liftInstruction a button press, represented by a new {@link LiftInstruction}
	 * @param add             whether to actually press the button or just perform a dry run
	 * @return the distance the lift has to travel before fulfilling the instruction or -1 if button can't be pressed
	 */
	public double pressButton(LiftInstruction liftInstruction, boolean add) {
		if (isClientside) {
			return -1;
		}

		final int buttonFloor = liftInstruction.getFloor();
		if (buttonFloor < 0 || buttonFloor >= floors.size()) {
			return -1;
		}

		final long buttonProgress = getProgress(buttonFloor);
		final LiftDirection buttonDirection = liftInstruction.getDirection();
		// Track the lift direction, progress, and distance covered when iterating through the existing instructions
		LiftDirection tempDirection = getDirection();
		double tempProgress = railProgress + speed * speed / 2 / Siding.ACCELERATION_DEFAULT * Math.signum(speed);
		double distance = 0;

		for (int i = 0; i < instructions.size(); i++) {
			final LiftInstruction nextInstruction = instructions.get(i);

			if (liftInstruction.equals(nextInstruction)) {
				return -1;
			}

			final long nextInstructionProgress = getProgress(nextInstruction.getFloor());
			final LiftDirection nextInstructionDirection = nextInstruction.getDirection();
			final LiftDirection directionToNextFloor = LiftDirection.fromDifference(nextInstructionProgress - tempProgress);

			// If button press comes from inside the lift or is in the same direction as the lift, slot it in as soon as possible
			final boolean condition1 = (buttonDirection == LiftDirection.NONE || buttonDirection == directionToNextFloor) && Utilities.isBetween(buttonProgress, tempProgress, nextInstructionProgress);
			// If the lift is going to change directions at the next instruction, slot in any remaining button presses that would have be visited if the lift didn't change direction
			final boolean condition2 = nextInstructionDirection != LiftDirection.NONE && nextInstructionDirection != directionToNextFloor && LiftDirection.fromDifference(buttonProgress - nextInstructionProgress) == (directionToNextFloor == LiftDirection.NONE ? tempDirection : directionToNextFloor);

			if (condition1 || condition2) {
				if (add) {
					instructions.add(i, liftInstruction);
					needsUpdate = true;
				}
				return distance + Math.abs(buttonProgress - tempProgress);
			}

			distance += Math.abs(nextInstructionProgress - tempProgress);
			tempDirection = directionToNextFloor;
			tempProgress = nextInstructionProgress;
		}

		// Add any remaining instructions that didn't satisfy the above conditions to the end of the list
		if (add) {
			instructions.add(liftInstruction);
			needsUpdate = true;
		}
		return distance + Math.abs(buttonProgress - tempProgress);
	}

	public double getHeight() {
		return height;
	}

	public double getWidth() {
		return width;
	}

	public double getDepth() {
		return depth;
	}

	public double getOffsetX() {
		return offsetX;
	}

	public double getOffsetY() {
		return offsetY;
	}

	public double getOffsetZ() {
		return offsetZ;
	}

	public boolean getIsDoubleSided() {
		return isDoubleSided;
	}

	public String getStyle() {
		return style;
	}

	public Angle getAngle() {
		return angle;
	}

	public Vector getPosition(BiFunction<Position, Position, ObjectArrayList<Vector>> trackProvider) {
		return currentFloorCallback((percentage, index) -> {
			final ObjectArrayList<Vector> trackPositions = trackProvider.apply(floors.get(index - 1).getPosition(), floors.get(index).getPosition());
			final double progress = percentage * (trackPositions.size() - 1);
			final int trackIndex = (int) Math.floor(progress);
			final double trackPercentage = progress - trackIndex;
			final Vector position1 = Utilities.getElement(trackPositions, trackIndex, new Vector(0, 0, 0));
			final Vector position2 = Utilities.getElement(trackPositions, trackIndex + 1, new Vector(0, 0, 0));
			return new Vector(
					getValueFromPercentage(trackPercentage, position1.x, position2.x),
					getValueFromPercentage(trackPercentage, position1.y, position2.y),
					getValueFromPercentage(trackPercentage, position1.z, position2.z)
			);
		}, new Vector(0, 0, 0));
	}

	public float getDoorValue() {
		if (stoppingCoolDown < DOOR_DELAY) {
			return 0;
		} else if (stoppingCoolDown < DOOR_DELAY + DOOR_MOVE_TIME) {
			return (float) (stoppingCoolDown - DOOR_DELAY) / DOOR_MOVE_TIME;
		} else if (stoppingCoolDown <= DOOR_DELAY + DOOR_MOVE_TIME + DOOR_OPEN_TIME) {
			return 1;
		} else {
			return (float) (DOOR_DELAY + DOOR_MOVE_TIME * 2 + DOOR_OPEN_TIME - stoppingCoolDown) / DOOR_MOVE_TIME;
		}
	}

	public boolean hasCoolDown() {
		return stoppingCoolDown > 0;
	}

	public void iterateFloors(Consumer<LiftFloor> consumer) {
		floors.forEach(consumer);
	}

	public int getFloorCount() {
		return floors.size();
	}

	public ObjectArraySet<LiftDirection> hasInstruction(int floor) {
		final ObjectArraySet<LiftDirection> liftDirections = new ObjectArraySet<>();
		instructions.forEach(liftInstruction -> {
			if (liftInstruction.getFloor() == floor) {
				liftDirections.add(liftInstruction.getDirection());
			}
		});
		return liftDirections;
	}

	public LiftFloor getCurrentFloor() {
		return currentFloorCallback((percentage, index) -> floors.get(index - (percentage < 0.5 ? 1 : 0)), new LiftFloor(new Position(0, 0, 0)));
	}

	public LiftDirection getDirection() {
		if (instructions.isEmpty()) {
			return LiftDirection.NONE;
		} else {
			return LiftDirection.fromDifference(getProgress(instructions.get(0).getFloor()) - railProgress);
		}
	}

	public void setDimensions(double height, double width, double depth, double offsetX, double offsetY, double offsetZ) {
		this.height = height;
		this.width = width;
		this.depth = depth;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
	}

	public void setIsDoubleSided(boolean isDoubleSided) {
		this.isDoubleSided = isDoubleSided;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	public void setAngle(Angle angle) {
		this.angle = angle;
	}

	public void setFloors(ObjectArrayList<LiftFloor> liftFloors) {
		floors.clear();
		floors.addAll(liftFloors);
		instructions.clear();
		setDistances();
		needsUpdate = true;
	}

	public void setFloors(Lift lift) {
		if (lift != this) {
			setFloors(lift.floors);
		}
	}

	public int getFloorIndex(Position position) {
		for (int i = 0; i < floors.size(); i++) {
			if (position.equals(floors.get(i).getPosition())) {
				return i;
			}
		}
		return -1;
	}

	public boolean overlappingFloors(Lift lift) {
		return lift.floors.stream().anyMatch(liftFloor -> getFloorIndex(liftFloor.getPosition()) >= 0);
	}

	public void updateFloor(LiftFloor liftFloor) {
		floors.forEach(currentFloor -> {
			if (currentFloor.getPosition().equals(liftFloor.getPosition())) {
				currentFloor.setNumberAndDescription(liftFloor.getNumber(), liftFloor.getDescription());
			}
		});
	}

	private void setDistances() {
		distances.clear();
		long minX = Long.MAX_VALUE;
		long maxX = -Long.MAX_VALUE;
		long minY = Long.MAX_VALUE;
		long maxY = -Long.MAX_VALUE;
		long minZ = Long.MAX_VALUE;
		long maxZ = -Long.MAX_VALUE;
		long distance = 0;

		for (int i = 0; i < floors.size(); i++) {
			final Position position = floors.get(i).getPosition();
			minX = Math.min(minX, position.getX());
			maxX = Math.max(maxX, position.getX());
			minY = Math.min(minY, position.getY());
			maxY = Math.max(maxY, position.getY());
			minZ = Math.min(minZ, position.getZ());
			maxZ = Math.max(maxZ, position.getZ());

			if (i == 0) {
				distances.add(0);
			} else {
				distance += position.manhattanDistance(floors.get(i - 1).getPosition());
				distances.add(distance);
			}
		}

		minPosition = new Position(minX, minY, minZ);
		maxPosition = new Position(maxX, maxY, maxZ);
	}

	/**
	 * @param floor the floor to check (an index of the list of all floors)
	 * @return the distance along the track from the very first floor
	 */
	private long getProgress(int floor) {
		return distances.isEmpty() ? 0 : distances.getLong(Utilities.clamp(floor, 0, distances.size() - 1));
	}

	private <T> T currentFloorCallback(PercentageCallback<T> percentageCallback, T defaultValue) {
		for (int i = 1; i < Math.min(distances.size(), floors.size()); i++) {
			final long distance1 = distances.getLong(i - 1);
			final long distance2 = distances.getLong(i);
			if (Utilities.isBetween(railProgress, distance1, distance2)) {
				return percentageCallback.apply((railProgress - distance1) / (distance2 - distance1), i);
			}
		}
		return defaultValue;
	}

	private static double getValueFromPercentage(double percentage, double value1, double value2) {
		return percentage * (value2 - value1) + value1;
	}

	@FunctionalInterface
	private interface PercentageCallback<T> {
		T apply(double percentage, int index);
	}
}
