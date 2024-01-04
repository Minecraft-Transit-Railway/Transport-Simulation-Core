package org.mtr.core.data;

import org.mtr.core.generated.data.LiftInstructionSchema;
import org.mtr.core.serializer.ReaderBase;

public class LiftInstruction extends LiftInstructionSchema {

	public LiftInstruction(int floor, LiftDirection direction) {
		super(floor, direction);
	}

	public LiftInstruction(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public int getFloor() {
		return (int) floor;
	}

	public LiftDirection getDirection() {
		return direction;
	}
}
