package org.mtr.core.operation;

import org.mtr.core.data.Data;
import org.mtr.core.generated.operation.ListDataResponseSchema;
import org.mtr.core.serializer.ReaderBase;


public final class ListDataResponse extends ListDataResponseSchema {

	private final Data data;

	public ListDataResponse(Data data) {
		this.data = data;
	}

	public ListDataResponse(ReaderBase readerBase, Data data) {
		super(readerBase);
		this.data = data;
		updateData(readerBase);
	}

	@Override
	protected Data stationsDataParameter() {
		return data;
	}

	@Override
	protected Data platformsDataParameter() {
		return data;
	}

	@Override
	protected Data sidingsDataParameter() {
		return data;
	}

	@Override
	protected Data routesDataParameter() {
		return data;
	}

	@Override
	protected Data depotsDataParameter() {
		return data;
	}

	@Override
	protected Data homesDataParameter() {
		return data;
	}

	@Override
	protected Data landmarksDataParameter() {
		return data;
	}

	public ListDataResponse list() {
		stations.addAll(data.stations);
		platforms.addAll(data.platforms);
		sidings.addAll(data.sidings);
		routes.addAll(data.routes);
		depots.addAll(data.depots);
		homes.addAll(data.homes);
		landmarks.addAll(data.landmarks);
		return this;
	}

	public void write() {
		data.stations.clear();
		data.stations.addAll(stations);
		data.platforms.clear();
		data.platforms.addAll(platforms);
		data.sidings.clear();
		data.sidings.addAll(sidings);
		data.routes.clear();
		data.routes.addAll(routes);
		data.depots.clear();
		data.depots.addAll(depots);
		data.homes.clear();
		data.homes.addAll(homes);
		data.landmarks.clear();
		data.landmarks.addAll(landmarks);
		data.sync();
	}
}
