package org.mtr.core.operation;

import org.mtr.core.data.*;
import org.mtr.core.generated.operation.UpdateDataResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class UpdateDataResponse extends UpdateDataResponseSchema {

	private final Data data;

	public UpdateDataResponse(Data data) {
		this.data = data;
	}

	public UpdateDataResponse(ReaderBase readerBase, Data data) {
		super(readerBase);
		this.data = data;
		updateData(readerBase);
	}

	@Nonnull
	@Override
	protected Data stationsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data platformsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data sidingsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data routesDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data depotsDataParameter() {
		return data;
	}

	public void write() {
		stations.forEach(station -> update(station, data.stations, data.stationIdMap.get(station.getId())));
		platforms.forEach(platform -> update(platform, data.platforms, data.platformIdMap.get(platform.getId())));
		sidings.forEach(siding -> update(siding, data.sidings, data.sidingIdMap.get(siding.getId())));
		routes.forEach(route -> update(route, data.routes, data.routeIdMap.get(route.getId())));
		depots.forEach(depot -> update(depot, data.depots, data.depotIdMap.get(depot.getId())));
		rails.forEach(rail -> update(rail, data.rails, data.railIdMap.get(rail.getHexId())));
		if (data instanceof ClientData) {
			simplifiedRoutes.forEach(simplifiedRoute -> update(simplifiedRoute, ((ClientData) data).simplifiedRoutes, ((ClientData) data).simplifiedRoutes.stream().filter(existingSimplifiedRoute -> existingSimplifiedRoute.getId() == simplifiedRoute.getId()).findFirst().orElse(null)));
		}
		data.sync();
	}

	public void addDepot(Depot depot) {
		depots.add(depot);
	}

	ObjectArrayList<Station> getStations() {
		return stations;
	}

	ObjectArrayList<Platform> getPlatforms() {
		return platforms;
	}

	ObjectArrayList<Siding> getSidings() {
		return sidings;
	}

	ObjectArrayList<Route> getRoutes() {
		return routes;
	}

	ObjectArrayList<SimplifiedRoute> getSimplifiedRoutes() {
		return simplifiedRoutes;
	}

	ObjectArrayList<Depot> getDepots() {
		return depots;
	}

	ObjectArrayList<Rail> getRails() {
		return rails;
	}

	private static <T extends SerializedDataBase> void update(T newData, ObjectSet<T> dataSet, @Nullable T existingData) {
		if (existingData != null) {
			dataSet.remove(existingData);
		}
		dataSet.add(newData);
	}
}
