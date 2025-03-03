package org.mtr.core.operation;

import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.operation.DeleteDataRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

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

	public DeleteDataResponse delete(Simulator simulator) {
		final DeleteDataResponse deleteDataResponse = new DeleteDataResponse();
		final ObjectArraySet<Position> railNodePositionsToUpdate = new ObjectArraySet<>();

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
		railIds.forEach(railId -> delete(simulator.railIdMap.get(railId), simulator.rails, railId, deleteDataResponse.getRailIds(), railNodePositionsToUpdate));
		railNodePositions.forEach(railNodePosition -> simulator.positionsToRail.getOrDefault(railNodePosition, new Object2ObjectOpenHashMap<>()).values().forEach(rail -> delete(rail, simulator.rails, rail.getHexId(), deleteDataResponse.getRailIds(), railNodePositionsToUpdate)));

		simulator.sync();
		railNodePositionsToUpdate.forEach(railNodePosition -> {
			if (simulator.positionsToRail.getOrDefault(railNodePosition, new Object2ObjectOpenHashMap<>()).isEmpty()) {
				deleteDataResponse.getRailNodePositions().add(railNodePosition);
			}
		});

		return deleteDataResponse;
	}

	private static <T extends NameColorDataBase> void delete(long id, ObjectArraySet<T> dataSet, LongArrayList dataToUpdate) {
		if (dataSet.removeIf(data -> data.getId() == id)) {
			dataToUpdate.add(id);
		}
	}

	private static void delete(@Nullable Rail rail, ObjectArraySet<Rail> rails, String railId, ObjectArrayList<String> railsIdsToUpdate, ObjectArraySet<Position> railNodePositionsToUpdate) {
		if (rail != null) {
			rails.remove(rail);
			railsIdsToUpdate.add(railId);
			rail.writePositions(railNodePositionsToUpdate);
		}
	}
}
