package org.mtr.core.operation;

import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.operation.DeleteDataRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import javax.annotation.Nullable;

public final class DeleteDataRequest extends DeleteDataRequestSchema {

	public DeleteDataRequest() {
	}

	public DeleteDataRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public DeleteDataRequest addStationId(long stationId) {
		stationIds.add(stationId);
		return this;
	}

	public DeleteDataRequest addPlatformId(long platformId) {
		platformIds.add(platformId);
		return this;
	}

	public DeleteDataRequest addSidingId(long sidingId) {
		sidingIds.add(sidingId);
		return this;
	}

	public DeleteDataRequest addRouteId(long routeId) {
		routeIds.add(routeId);
		return this;
	}

	public DeleteDataRequest addDepotId(long depotId) {
		depotIds.add(depotId);
		return this;
	}

	public DeleteDataRequest addLiftFloorPosition(Position liftFloorPosition) {
		liftFloorPositions.add(liftFloorPosition);
		return this;
	}

	public DeleteDataRequest addRailId(String railId) {
		railIds.add(railId);
		return this;
	}

	public DeleteDataRequest addRailNodePosition(Position position) {
		railNodePositions.add(position);
		return this;
	}

	public JsonObject delete(Simulator simulator) {
		final DeleteDataResponse deleteDataResponse = new DeleteDataResponse();
		final ObjectOpenHashSet<Position> railNodePositionsToUpdate = new ObjectOpenHashSet<>();

		stationIds.forEach(stationId -> delete(stationId, simulator.stations, deleteDataResponse.getStationIds()));
		platformIds.forEach(platformId -> delete(platformId, simulator.platforms, deleteDataResponse.getPlatformIds()));
		sidingIds.forEach(sidingId -> delete(sidingId, simulator.sidings, deleteDataResponse.getSidingIds()));
		routeIds.forEach(routeId -> delete(routeId, simulator.routes, deleteDataResponse.getRouteIds()));
		depotIds.forEach(depotId -> delete(depotId, simulator.depots, deleteDataResponse.getDepotIds()));
		liftFloorPositions.forEach(liftPosition -> simulator.lifts.removeIf(lift -> {
			if (lift.getFloorIndex(liftPosition) >= 0) {
				deleteDataResponse.getLiftIds().add(lift.getId());
				return true;
			} else {
				return false;
			}
		}));
		railIds.forEach(railId -> delete(simulator.railIdMap.get(railId), railId, deleteDataResponse.getRailIds(), railNodePositionsToUpdate));
		railNodePositions.forEach(railNodePosition -> simulator.positionsToRail.getOrDefault(railNodePosition, new Object2ObjectOpenHashMap<>()).values().forEach(rail -> delete(rail, rail.getHexId(), deleteDataResponse.getRailIds(), railNodePositionsToUpdate)));

		simulator.sync();
		railNodePositionsToUpdate.forEach(railNodePosition -> {
			if (simulator.positionsToRail.getOrDefault(railNodePosition, new Object2ObjectOpenHashMap<>()).isEmpty()) {
				deleteDataResponse.getRailNodePositions().add(railNodePosition);
			}
		});

		return Utilities.getJsonObjectFromData(deleteDataResponse);
	}

	private static <T extends NameColorDataBase> void delete(long id, ObjectAVLTreeSet<T> dataSet, LongArrayList dataToUpdate) {
		if (dataSet.removeIf(data -> data.getId() == id)) {
			dataToUpdate.add(id);
		}
	}

	private static void delete(@Nullable Rail rail, String railId, ObjectArrayList<String> railsIdsToUpdate, ObjectOpenHashSet<Position> railNodePositionsToUpdate) {
		if (rail != null) {
			railsIdsToUpdate.add(railId);
			rail.writePositions(railNodePositionsToUpdate);
		}
	}
}
