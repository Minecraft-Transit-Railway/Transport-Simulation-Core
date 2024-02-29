package org.mtr.core.operation;

import org.mtr.core.data.*;
import org.mtr.core.generated.operation.DataResponseSchema;
import org.mtr.core.serializer.ReaderBase;

import javax.annotation.Nonnull;

public final class DataResponse extends DataResponseSchema {

	private final Data data;

	DataResponse(Data data) {
		super();
		this.data = data;
	}

	public DataResponse(ReaderBase readerBase, ClientData data) {
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
	protected Data depotsDataParameter() {
		return data;
	}

	public void write() {
		if (data instanceof ClientData && (!stations.isEmpty() || !platforms.isEmpty() || !sidings.isEmpty() || !simplifiedRoutes.isEmpty() || !depots.isEmpty() || !rails.isEmpty())) {
			data.stations.removeIf(station -> !stationsToKeep.contains(station.getId()));
			data.stations.addAll(stations);
			data.platforms.removeIf(platform -> !platformsToKeep.contains(platform.getId()));
			data.platforms.addAll(platforms);
			data.sidings.removeIf(siding -> !sidingsToKeep.contains(siding.getId()));
			data.sidings.addAll(sidings);
			((ClientData) data).simplifiedRoutes.removeIf(simplifiedRoute -> !simplifiedRoutesToKeep.contains(simplifiedRoute.getId()));
			((ClientData) data).simplifiedRoutes.addAll(simplifiedRoutes);
			data.depots.removeIf(depot -> !depotsToKeep.contains(depot.getId()));
			data.depots.addAll(depots);
			data.rails.removeIf(rail -> !railsToKeep.contains(rail.getHexId()));
			data.rails.addAll(rails);
			data.sync();
		}
	}

	void addStation(Station station) {
		stations.add(station);
	}

	void addStation(long stationId) {
		stationsToKeep.add(stationId);
	}

	void addPlatform(Platform platform) {
		platforms.add(platform);
	}

	void addPlatform(long platformId) {
		platformsToKeep.add(platformId);
	}

	void addSiding(Siding siding) {
		sidings.add(siding);
	}

	void addSiding(long sidingId) {
		sidingsToKeep.add(sidingId);
	}

	void addDepot(Depot depot) {
		depots.add(depot);
	}

	void addDepot(long depotId) {
		depotsToKeep.add(depotId);
	}

	void addRoute(Route route) {
		SimplifiedRoute.addToList(simplifiedRoutes, route);
	}

	void addRoute(long routeId) {
		simplifiedRoutesToKeep.add(routeId);
	}

	void addRail(Rail rail) {
		rails.add(rail);
	}

	void addRail(String railId) {
		railsToKeep.add(railId);
	}
}
