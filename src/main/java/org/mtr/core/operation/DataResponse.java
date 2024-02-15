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
			data.stations.addAll(stations);
			data.platforms.addAll(platforms);
			data.sidings.addAll(sidings);
			((ClientData) data).simplifiedRoutes.addAll(simplifiedRoutes);
			data.depots.addAll(depots);
			data.rails.addAll(rails);
			data.sync();
		}
	}

	void addStation(Station station) {
		stations.add(station);
	}

	void addPlatform(Platform platform) {
		platforms.add(platform);
	}

	void addSiding(Siding siding) {
		sidings.add(siding);
	}

	void addDepot(Depot depot) {
		depots.add(depot);
	}

	void addRoute(Route route) {
		SimplifiedRoute.addToList(simplifiedRoutes, route);
	}

	void addRail(Rail rail) {
		rails.add(rail);
	}
}
