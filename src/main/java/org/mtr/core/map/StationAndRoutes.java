package org.mtr.core.map;

import org.mtr.core.generated.map.StationsAndRoutesSchema;

public final class StationAndRoutes extends StationsAndRoutesSchema {

	public StationAndRoutes() {
		super();
	}

	public void addStation(org.mtr.core.data.Station station) {
		stations.add(new Station(station));
	}

	public void addRoute(org.mtr.core.data.Route route) {
		routes.add(new Route(route));
	}
}
