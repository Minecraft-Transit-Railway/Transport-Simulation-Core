package org.mtr.core.map;

import org.mtr.core.generated.map.RouteSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Route extends RouteSchema {

	public Route(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	Route(org.mtr.core.data.Route route) {
		super(route.getName(), route.getColorHex(), route.getRouteNumber(), route.getRouteTypeKey(), route.getCircularState());
		route.getRoutePlatforms().forEach(routePlatformData -> {
			if (routePlatformData.platform != null && routePlatformData.platform.area != null) {
				stations.add(RouteStation.create(routePlatformData.platform));
			}
		});
	}
}
