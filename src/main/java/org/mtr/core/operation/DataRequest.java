package org.mtr.core.operation;

import org.mtr.core.data.Client;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Position;
import org.mtr.core.generated.operation.DataRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;

public final class DataRequest extends DataRequestSchema {

	public DataRequest(String clientId, Position clientPosition, long requestRadius) {
		super(clientId, clientPosition, requestRadius);
	}

	public DataRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public JsonObject getData(Simulator simulator) {
		final DataResponse dataResponse = new DataResponse(simulator);
		simulator.stations.forEach(station -> {
			if (!existingStationIds.contains(station.getId()) && station.inArea(clientPosition, requestRadius)) {
				dataResponse.addStation(station);
			}
		});
		simulator.platforms.forEach(platform -> {
			if (!existingPlatformIds.contains(platform.getId()) && platform.closeTo(clientPosition, requestRadius)) {
				dataResponse.addPlatform(platform);
				platform.routes.forEach(route -> {
					if (!existingSimplifiedRouteIds.contains(route.getId())) {
						dataResponse.addRoute(route);
					}
				});
			}
		});
		simulator.sidings.forEach(siding -> {
			if (!existingSidingIds.contains(siding.getId()) && siding.closeTo(clientPosition, requestRadius)) {
				dataResponse.addSiding(siding);
			}
		});
		simulator.depots.forEach(depot -> {
			if (!existingDepotIds.contains(depot.getId()) && depot.inArea(clientPosition, requestRadius)) {
				dataResponse.addDepot(depot);
			}
		});
		simulator.railIdMap.forEach((railId, rail) -> {
			if (!existingRailIds.contains(railId) && rail.closeTo(clientPosition, requestRadius)) {
				dataResponse.addRail(rail);
			}
		});
		simulator.clients.computeIfAbsent(clientId, key -> new Client(clientId)).setPositionAndUpdateRadius(clientPosition, requestRadius);
		return Utilities.getJsonObjectFromData(dataResponse);
	}

	public void writeExistingIds(ClientData clientData) {
		existingStationIds.addAll(clientData.stationIdMap.keySet());
		existingPlatformIds.addAll(clientData.platformIdMap.keySet());
		existingSidingIds.addAll(clientData.sidingIdMap.keySet());
		existingSimplifiedRouteIds.addAll(clientData.simplifiedRouteIds);
		existingDepotIds.addAll(clientData.depotIdMap.keySet());
		existingRailIds.addAll(clientData.railIdMap.keySet());
	}
}
