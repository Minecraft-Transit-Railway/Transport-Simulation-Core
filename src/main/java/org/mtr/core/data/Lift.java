package org.mtr.core.data;

import org.mtr.core.generated.data.LiftSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class Lift extends LiftSchema {

	private final LongArrayList distances = new LongArrayList();

	public Lift(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Lift(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
		setDistances();
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public void tick() {

	}

	/**
	 * @param liftInstruction a button press, represented by a new {@link LiftInstruction}
	 * @param add             whether to actually press the button or just perform a dry run
	 * @return the distance the lift has to travel before fulfilling the instruction or -1 if button can't be pressed
	 */
	public long pressButton(LiftInstruction liftInstruction, boolean add) {
		final int buttonFloor = liftInstruction.getFloor();
		final LiftDirection buttonDirection = liftInstruction.getDirection();
		if (buttonFloor < 0 || buttonFloor >= floors.size() || buttonDirection == LiftDirection.NONE && (direction == LiftDirection.UP && buttonFloor < currentFloor || direction == LiftDirection.DOWN && buttonFloor > currentFloor)) {
			return -1;
		}

		// Track the lift direction and floor when iterating through the existing instructions
		LiftDirection tempDirection = direction == LiftDirection.NONE ? LiftDirection.fromDifference((instructions.isEmpty() ? buttonFloor : instructions.get(0).getFloor()) - currentFloor) : direction;
		int tempFloor = (int) Math.floor(currentFloor);
		long distance = (int) (distances.getLong(tempFloor) * (tempFloor - currentFloor) * tempDirection.sign);

		for (int i = 0; i < instructions.size(); i++) {
			final LiftInstruction nextInstruction = instructions.get(i);
			final int nextFloor = nextInstruction.getFloor();
			final LiftDirection nextDirection = nextInstruction.getDirection();

			// If button press comes from inside the lift or is in the same direction as the lift, slot it in as soon as possible
			if ((buttonDirection == LiftDirection.NONE || buttonDirection == tempDirection) && Utilities.isBetween(buttonFloor, tempFloor, nextFloor)) {
				if (add) {
					instructions.add(i, liftInstruction);
				}
				return distance + getDistance(tempFloor, buttonFloor);
			}

			if (nextDirection != LiftDirection.NONE && nextDirection != tempDirection) {
				// If the lift is going to change directions at the next instruction, slot in any remaining button presses that will be visited if the lift didn't change direction
				if (LiftDirection.fromDifference(buttonFloor - tempFloor) == tempDirection) {
					if (add) {
						instructions.add(i, liftInstruction);
					}
					return distance + getDistance(tempFloor, buttonFloor);
				}

				tempDirection = nextDirection;
			}

			distance += getDistance(tempFloor, nextFloor);
			tempFloor = nextFloor;
		}

		// Add any remaining instructions that didn't satisfy the above conditions to the end of the list
		if (add) {
			instructions.add(liftInstruction);
		}
		return distance;
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
		return direction;
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
		for (int i = 1; i < floors.size(); i++) {
			distances.add(floors.get(i).getPosition().manhattanDistance(floors.get(i - 1).getPosition()));
		}
		distances.add(0);
	}

	private long getDistance(int floor1, int floor2) {
		final int lowerFloor = Math.max(Math.min(floor1, floor2), 0);
		final int upperFloor = Math.min(Math.max(floor1, floor2), distances.size());
		long distance = 0;
		for (int i = lowerFloor; i < upperFloor; i++) {
			distance += distances.getLong(i);
		}
		return distance;
	}
}
