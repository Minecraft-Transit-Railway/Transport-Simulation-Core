package org.mtr.core.data;

import org.mtr.core.generated.data.LiftInstructionSchema;
import org.mtr.core.serializer.ReaderBase;

import java.util.Objects;

/**
 * A pending lift movement: "go to floor X, then continue moving in direction Y".
 *
 * <p>Lift instructions sit in a {@code Lift}'s instruction queue. Each entry pairs the target
 * floor index with the {@link LiftDirection} the cabin should commit to once it arrives — this
 * lets the simulator pick up further hall calls in the same direction without backtracking.</p>
 *
 * <p>Equality is by {@code (floor, direction)} so duplicate calls collapse naturally inside
 * fastutil sets / lists; {@link #hashCode()} is kept consistent with {@link #equals(Object)}.</p>
 */
public class LiftInstruction extends LiftInstructionSchema {

	/**
	 * Construct a new instruction targeting the given floor with the given continuation direction.
	 *
	 * @param floor     index of the target floor (0-based, matching {@link LiftFloor})
	 * @param direction the direction the cabin should continue serving once it reaches {@code floor}
	 */
	public LiftInstruction(int floor, LiftDirection direction) {
		super(floor, direction);
	}

	/**
	 * Deserialisation constructor used when restoring a lift's instruction queue from disk
	 * or from an incoming wire update.
	 *
	 * @param readerBase serialiser-agnostic reader over the persisted instruction payload
	 */
	public LiftInstruction(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	/**
	 * @return the target floor index (downcast from the stored {@code long} for ergonomic use)
	 */
	public int getFloor() {
		return (int) floor;
	}

	/**
	 * @return the direction the lift should continue serving once it reaches {@link #getFloor()}
	 */
	public LiftDirection getDirection() {
		return direction;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof final LiftInstruction other) {
			return floor == other.floor && direction == other.direction;
		} else {
			return super.equals(obj);
		}
	}

	@Override
	public int hashCode() {
		// Kept consistent with equals(Object) so LiftInstruction can live inside hash-based fastutil collections.
		return Objects.hash(floor, direction);
	}
}
