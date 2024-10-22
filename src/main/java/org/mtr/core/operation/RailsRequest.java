package org.mtr.core.operation;

import org.mtr.core.data.Rail;
import org.mtr.core.generated.operation.RailsRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public final class RailsRequest extends RailsRequestSchema {

	public RailsRequest() {
		super();
	}

	public RailsRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public RailsResponse query(Simulator simulator) {
		final RailsResponse railsResponse = new RailsResponse();
		railIds.forEach(railId -> {
			final Rail rail = simulator.railIdMap.get(railId);
			if (rail != null) {
				railsResponse.add(rail);
			}
		});
		return railsResponse;
	}

	public RailsRequest addRailId(String railId) {
		railIds.add(railId);
		return this;
	}
}
