package org.mtr.core.operation;

import org.mtr.core.data.*;
import org.mtr.core.generated.operation.UpdateDataResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nonnull;
import java.util.function.Function;

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

	@Nonnull
	@Override
	protected Data liftsDataParameter() {
		return data;
	}

	public void write() {
		stations.forEach(station -> update(station, data.stations, NameColorDataBase::getId));
		platforms.forEach(platform -> update(platform, data.platforms, NameColorDataBase::getId));
		sidings.forEach(siding -> update(siding, data.sidings, NameColorDataBase::getId));
		routes.forEach(route -> update(route, data.routes, NameColorDataBase::getId));
		depots.forEach(depot -> update(depot, data.depots, NameColorDataBase::getId));
		lifts.forEach(lift -> update(lift, data.lifts, NameColorDataBase::getId));
		rails.forEach(rail -> update(rail, data.rails, TwoPositionsBase::getHexId));
		if (data instanceof ClientData) {
			simplifiedRoutes.forEach(simplifiedRoute -> update(simplifiedRoute, ((ClientData) data).simplifiedRoutes, SimplifiedRoute::getId));
		}
		data.sync();
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

	ObjectArrayList<Lift> getLifts() {
		return lifts;
	}

	ObjectArrayList<Rail> getRails() {
		return rails;
	}

	private static <T extends SerializedDataBase, U> void update(T newData, ObjectSet<T> dataSet, Function<T, U> getId) {
		dataSet.removeIf(data -> getId.apply(data).equals(getId.apply(newData)));
		dataSet.add(newData);
	}
}
