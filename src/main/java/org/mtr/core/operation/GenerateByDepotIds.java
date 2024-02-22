package org.mtr.core.operation;

import org.mtr.core.data.Depot;
import org.mtr.core.generated.operation.GenerateByDepotIdsSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;

public final class GenerateByDepotIds extends GenerateByDepotIdsSchema {

	public GenerateByDepotIds() {
	}

	public GenerateByDepotIds(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void addDepotId(long depotId) {
		depotIds.add(depotId);
	}

	public JsonObject generate(Simulator simulator, Consumer<JsonObject> sendResponse) {
		final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
		depotIds.forEach(depotId -> {
			final Depot depot = simulator.depotIdMap.get(depotId);
			if (depot != null) {
				depots.add(depot);
			}
		});
		Depot.generateDepots(simulator, depots, sendResponse);
		return new JsonObject();
	}
}
