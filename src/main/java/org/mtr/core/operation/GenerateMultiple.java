package org.mtr.core.operation;

import org.mtr.core.generated.operation.GenerateMultipleSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;

public final class GenerateMultiple extends GenerateMultipleSchema {

	public GenerateMultiple() {
	}

	public GenerateMultiple(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public JsonObject generate(Simulator simulator) {
		simulator.generatePath(filter);
		return new JsonObject();
	}
}
