package org.mtr.core.operation;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.generated.operation.DeleteDataResponseSchema;
import org.mtr.core.serializer.ReaderBase;

import java.util.function.Consumer;

public final class DeleteDataResponse extends DeleteDataResponseSchema {

	public DeleteDataResponse() {
	}

	public DeleteDataResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void write(Data data) {
		data.stations.removeIf(station -> stationIds.contains(station.getId()));
		data.platforms.removeIf(platform -> platformIds.contains(platform.getId()));
		data.sidings.removeIf(siding -> sidingIds.contains(siding.getId()));
		data.routes.removeIf(route -> routeIds.contains(route.getId()));
		data.depots.removeIf(depot -> depotIds.contains(depot.getId()));
		data.lifts.removeIf(lift -> liftIds.contains(lift.getId()));
		data.rails.removeIf(rail -> railIds.contains(rail.getHexId()));
		data.homes.removeIf(home -> homeIds.contains(home.getId()));
		data.landmarks.removeIf(landmark -> landmarkIds.contains(landmark.getId()));
		if (data instanceof ClientData) {
			((ClientData) data).simplifiedRoutes.removeIf(simplifiedRoute -> routeIds.contains(simplifiedRoute.getId()));
		}
		data.sync();
	}

	public void iterateRailNodePosition(Consumer<Position> consumer) {
		railNodePositions.forEach(consumer);
	}

	LongArrayList getStationIds() {
		return stationIds;
	}

	LongArrayList getPlatformIds() {
		return platformIds;
	}

	LongArrayList getSidingIds() {
		return sidingIds;
	}

	LongArrayList getRouteIds() {
		return routeIds;
	}

	LongArrayList getDepotIds() {
		return depotIds;
	}

	LongArrayList getLiftIds() {
		return liftIds;
	}

	ObjectArrayList<String> getRailIds() {
		return railIds;
	}

	ObjectArrayList<Position> getRailNodePositions() {
		return railNodePositions;
	}

	LongArrayList getHomeIds() {
		return homeIds;
	}

	LongArrayList getLandmarkIds() {
		return landmarkIds;
	}
}
