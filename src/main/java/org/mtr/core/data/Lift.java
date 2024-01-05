package org.mtr.core.data;

import org.mtr.core.generated.data.LiftSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class Lift extends LiftSchema {

	private long stoppingCoolDown;
	private boolean needsUpdate;
	private final LongArrayList distances = new LongArrayList();
	/**
	 * If a lift is clientside, don't close the doors or add instructions. Always wait for a socket update instead.
	 */
	private final boolean isClientside;

	private static final int MAX_SPEED = 10 / Depot.MILLIS_PER_SECOND; // 10 m/s
	private static final int STOPPING_TIME = 4000;

	public Lift(Data data) {
		super(TransportMode.values()[0], data);
		this.isClientside = !(data instanceof Simulator);
	}

	public Lift(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		this.isClientside = !(data instanceof Simulator);
		updateData(readerBase);
		setDistances();
	}

	/**
	 * @deprecated for {@link org.mtr.core.integration.Integration} use only
	 */
	@Deprecated
	public Lift(ReaderBase readerBase) {
		this(readerBase, new Data());
	}

	@Override
	public boolean isValid() {
		return true;
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
				speed = speed - Siding.ACCELERATION_DEFAULT * millisElapsed * Math.signum(speed);

				if (Math.abs(speed) < Siding.ACCELERATION_DEFAULT) {
					speed = 0;
				}
			} else {
				final long nextInstructionProgress = getProgress(instructions.get(0).getFloor());
				speed = Utilities.clamp(speed + Siding.ACCELERATION_DEFAULT * millisElapsed * Math.signum(nextInstructionProgress - railProgress), -MAX_SPEED, MAX_SPEED);

				if (Math.abs(railProgress - nextInstructionProgress) < Siding.ACCELERATION_DEFAULT) {
					railProgress = nextInstructionProgress;
					speed = 0;
					instructions.remove(0);
					stoppingCoolDown = STOPPING_TIME;
				}
			}

			railProgress = Utilities.clamp(speed * millisElapsed, 0, getProgress(Integer.MAX_VALUE));
		}

		if (!isClientside && data instanceof Simulator) {
			final double updateRadius = ((Simulator) data).clientGroup.getUpdateRadius();
			((Simulator) data).clientGroup.iterateClients(client -> {
				final Position position = client.getPosition();
				if (floors.stream().anyMatch(liftFloor -> liftFloor.getPosition().manhattanDistance(position) <= updateRadius)) {
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
		final int buttonFloor = liftInstruction.getFloor();
		final LiftDirection buttonDirection = liftInstruction.getDirection();
		if (buttonFloor < 0 || buttonFloor >= floors.size()) {
			return -1;
		}

		// Track the lift progress and distance covered when iterating through the existing instructions
		double tempProgress = railProgress + speed * speed / 2 / Siding.ACCELERATION_DEFAULT * Math.signum(speed);
		double distance = 0;

		for (int i = 0; i < instructions.size(); i++) {
			final LiftInstruction nextInstruction = instructions.get(i);
			final long nextInstructionProgress = getProgress(nextInstruction.getFloor());
			final LiftDirection nextInstructionDirection = nextInstruction.getDirection();
			final LiftDirection directionToNextFloor = LiftDirection.fromDifference(nextInstructionProgress - railProgress);

			// If button press comes from inside the lift or is in the same direction as the lift, slot it in as soon as possible
			final boolean condition1 = (buttonDirection == LiftDirection.NONE || buttonDirection == directionToNextFloor) && Utilities.isBetween(buttonFloor, tempProgress, nextInstructionProgress);
			// If the lift is going to change directions at the next instruction, slot in any remaining button presses that would have be visited if the lift didn't change direction
			final boolean condition2 = nextInstructionDirection != LiftDirection.NONE && nextInstructionDirection != directionToNextFloor && LiftDirection.fromDifference(buttonFloor - tempProgress) == directionToNextFloor;

			if (condition1 || condition2) {
				if (add) {
					instructions.add(i, liftInstruction);
					needsUpdate = true;
				}
				return distance + Math.abs(buttonFloor - tempProgress);
			}

			distance += Math.abs(nextInstructionProgress - tempProgress);
			tempProgress = nextInstructionProgress;
		}

		// Add any remaining instructions that didn't satisfy the above conditions to the end of the list
		if (add) {
			instructions.add(liftInstruction);
			needsUpdate = true;
		}
		return distance + Math.abs(buttonFloor - tempProgress);
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

	public double getSpeed() {
		return speed;
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

	public int getFloorIndex(Position position) {
		for (int i = 0; i < floors.size(); i++) {
			if (position.equals(floors.get(i).getPosition())) {
				return i;
			}
		}
		return -1;
	}

	private void setDistances() {
		distances.clear();
		long distance = 0;
		for (int i = 0; i < floors.size(); i++) {
			if (i == 0) {
				distances.add(0);
			} else {
				distance += floors.get(i).getPosition().manhattanDistance(floors.get(i - 1).getPosition());
				distances.add(distance);
			}
		}
	}

	/**
	 * @param floor the floor to check (an index of the list of all floors)
	 * @return the distance along the track from the very first floor
	 */
	private long getProgress(int floor) {
		return distances.getLong(Utilities.clamp(floor, 0, distances.size() - 1));
	}
}
