package org.mtr.core.operation;

import org.mtr.core.data.Data;
import org.mtr.core.generated.operation.ListDataResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.Nonnull;

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

	public JsonObject list() {
		stations.addAll(data.stations);
		platforms.addAll(data.platforms);
		sidings.addAll(data.sidings);
		routes.addAll(data.routes);
		depots.addAll(data.depots);
		return Utilities.getJsonObjectFromData(this);
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
		data.sync();
	}
}
