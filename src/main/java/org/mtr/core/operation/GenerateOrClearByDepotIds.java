package org.mtr.core.operation;

import org.mtr.core.data.Depot;
import org.mtr.core.generated.operation.GenerateOrClearByDepotIdsSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class GenerateOrClearByDepotIds extends GenerateOrClearByDepotIdsSchema {

	public GenerateOrClearByDepotIds() {
	}

	public GenerateOrClearByDepotIds(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void addDepotId(long depotId) {
		depotIds.add(depotId);
	}

	public void generate(Simulator simulator) {
		Depot.generateDepots(simulator, getDepots(simulator));
	}

	public void clear(Simulator simulator) {
		Depot.clearDepots(getDepots(simulator));
	}

	private ObjectArrayList<Depot> getDepots(Simulator simulator) {
		final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
		depotIds.forEach(depotId -> {
			final Depot depot = simulator.depotIdMap.get(depotId);
			if (depot != null) {
				depots.add(depot);
			}
		});
		return depots;
	}
}
