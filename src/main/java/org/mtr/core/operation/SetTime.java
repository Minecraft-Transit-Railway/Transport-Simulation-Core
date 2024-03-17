package org.mtr.core.operation;

import org.mtr.core.generated.operation.SetTimeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;

public final class SetTime extends SetTimeSchema {

	public SetTime(long gameMillis, long millisPerDay, boolean isTimeMoving) {
		super(gameMillis, millisPerDay, isTimeMoving);
	}

	public SetTime(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public JsonObject setGameTime(Simulator simulator) {
		simulator.setGameTime(gameMillis, millisPerDay, isTimeMoving);
		return new JsonObject();
	}
}
