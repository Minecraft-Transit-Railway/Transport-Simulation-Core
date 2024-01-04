package org.mtr.core.data;

import org.mtr.core.generated.data.LiftFloorSchema;
import org.mtr.core.serializer.ReaderBase;

public class LiftFloor extends LiftFloorSchema {

	public LiftFloor(Position position, String number, String description) {
		super(position, number, description);
	}

	public LiftFloor(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Position getPosition() {
		return position;
	}

	public String getNumber() {
		return number;
	}

	public String getDescription() {
		return description;
	}
}
