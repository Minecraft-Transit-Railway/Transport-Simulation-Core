package org.mtr.core.operation;

import org.mtr.core.generated.operation.SetTimeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public class SetTime extends SetTimeSchema {

	public SetTime(long gameMillis, long millisPerDay) {
		super(gameMillis, millisPerDay);
	}

	public SetTime(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void setGameTime(Simulator simulator) {
		simulator.setGameTime(gameMillis, millisPerDay);
	}
}
