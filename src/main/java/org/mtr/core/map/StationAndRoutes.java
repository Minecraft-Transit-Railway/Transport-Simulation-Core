package org.mtr.core.map;

import org.mtr.core.generated.map.StationsAndRoutesSchema;

import java.util.Arrays;

public final class StationAndRoutes extends StationsAndRoutesSchema {

	public StationAndRoutes(String[] dimensions) {
		super();
		this.dimensions.addAll(Arrays.asList(dimensions));
	}

	public void addStation(org.mtr.core.data.Station station) {
		stations.add(new Station(station));
	}

	public void addRoute(org.mtr.core.data.Route route) {
		routes.add(new Route(route));
	}
}
