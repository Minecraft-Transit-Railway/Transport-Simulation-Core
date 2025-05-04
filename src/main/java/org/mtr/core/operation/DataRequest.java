package org.mtr.core.operation;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.*;
import org.mtr.core.generated.operation.DataRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import java.util.UUID;

public final class DataRequest extends DataRequestSchema {

	private final UUID uuid;

	public DataRequest(UUID uuid, Position clientPosition, long requestRadius) {
		super(uuid.toString(), clientPosition, requestRadius);
		this.uuid = uuid;
	}

	public DataRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		uuid = UUID.fromString(clientId);
	}

	public DataResponse getData(Simulator simulator) {
		final DataResponse dataResponse = new DataResponse(simulator);
		final LongAVLTreeSet addedStationIds = new LongAVLTreeSet();
		final LongAVLTreeSet addedPlatformIds = new LongAVLTreeSet();
		final LongAVLTreeSet addedRouteIds = new LongAVLTreeSet();

		simulator.stations.forEach(station -> {
			if (station.inArea(clientPosition, requestRadius)) {
				final ObjectArrayList<Station> stationsToAdd = new ObjectArrayList<>();
				stationsToAdd.add(station);
				stationsToAdd.addAll(station.connectedStations);
				stationsToAdd.forEach(addStation -> {
					if (!addedStationIds.contains(addStation.getId())) {
						if (existingStationIds.contains(addStation.getId())) {
							dataResponse.addStation(addStation.getId());
						} else {
							dataResponse.addStation(addStation);
						}
						addedStationIds.add(addStation.getId());
						addStation.savedRails.forEach(platform -> addPlatform(platform, dataResponse, addedPlatformIds, addedRouteIds));
					}
				});
			}
		});

		simulator.platforms.forEach(platform -> {
			if (platform.closeTo(clientPosition, requestRadius)) {
				addPlatform(platform, dataResponse, addedPlatformIds, addedRouteIds);
			}
		});

		simulator.sidings.forEach(siding -> {
			if (siding.closeTo(clientPosition, requestRadius)) {
				if (existingSidingIds.contains(siding.getId())) {
					dataResponse.addSiding(siding.getId());
				} else {
					dataResponse.addSiding(siding);
				}
			}
		});

		simulator.depots.forEach(depot -> {
			if (depot.inArea(clientPosition, requestRadius)) {
				if (existingDepotIds.contains(depot.getId())) {
					dataResponse.addDepot(depot.getId());
				} else {
					dataResponse.addDepot(depot);
				}
			}
		});

		simulator.railIdMap.forEach((railId, rail) -> {
			if (rail.closeTo(clientPosition, requestRadius)) {
				if (existingRailIds.contains(railId)) {
					dataResponse.addRail(rail.getHexId());
				} else {
					dataResponse.addRail(rail);
				}
			}
		});

		simulator.clients.computeIfAbsent(uuid, key -> new Client(uuid)).setPositionAndUpdateRadius(clientPosition, requestRadius);
		return dataResponse;
	}

	public void writeExistingIds(ClientData clientData) {
		existingStationIds.addAll(clientData.stationIdMap.keySet());
		existingPlatformIds.addAll(clientData.platformIdMap.keySet());
		existingSidingIds.addAll(clientData.sidingIdMap.keySet());
		existingSimplifiedRouteIds.addAll(clientData.simplifiedRouteIds);
		existingDepotIds.addAll(clientData.depotIdMap.keySet());
		existingRailIds.addAll(clientData.railIdMap.keySet());
	}

	private void addPlatform(Platform platform, DataResponse dataResponse, LongAVLTreeSet addedPlatformIds, LongAVLTreeSet addedRouteIds) {
		if (!addedPlatformIds.contains(platform.getId())) {
			if (existingPlatformIds.contains(platform.getId())) {
				dataResponse.addPlatform(platform.getId());
			} else {
				dataResponse.addPlatform(platform);
			}
			addedPlatformIds.add(platform.getId());
			platform.routes.forEach(route -> {
				if (!addedRouteIds.contains(route.getId())) {
					if (existingSimplifiedRouteIds.contains(route.getId())) {
						dataResponse.addRoute(route.getId());
					} else {
						dataResponse.addRoute(route);
					}
					addedRouteIds.add(route.getId());
				}
			});
		}
	}
}
