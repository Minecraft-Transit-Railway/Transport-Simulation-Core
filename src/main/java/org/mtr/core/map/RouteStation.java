package org.mtr.core.map;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.generated.map.RouteStationSchema;
import org.mtr.core.serializer.ReaderBase;

public final class RouteStation extends RouteStationSchema {

	public RouteStation(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	private RouteStation(String id, Position position) {
		super(id, position.getX(), position.getY(), position.getZ());
	}

	static RouteStation create(Platform platform) {
		final Position position = platform.getMidPosition();
		return new RouteStation(platform.area.getHexId(), position);
	}
}
