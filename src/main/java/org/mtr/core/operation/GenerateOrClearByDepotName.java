package org.mtr.core.operation;

import org.mtr.core.data.Depot;
import org.mtr.core.generated.operation.GenerateOrClearByDepotNameSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;

import java.util.function.Consumer;

public final class GenerateOrClearByDepotName extends GenerateOrClearByDepotNameSchema {

	public GenerateOrClearByDepotName() {
	}

	public GenerateOrClearByDepotName(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public JsonObject generate(Simulator simulator, Consumer<JsonObject> sendResponse) {
		Depot.generateDepotsByName(simulator, filter, sendResponse);
		return new JsonObject();
	}

	public JsonObject clear(Simulator simulator) {
		Depot.clearDepotsByName(simulator, filter);
		return new JsonObject();
	}
}
