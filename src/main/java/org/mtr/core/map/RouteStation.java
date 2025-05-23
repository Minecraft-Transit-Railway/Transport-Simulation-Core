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

	private RouteStation(String id, Position position, String name, long dwellTime) {
		super(id, position.getX(), position.getY(), position.getZ(), name, dwellTime);
	}

	static RouteStation create(Platform platform) {
		final Position position = platform.getMidPosition();
		return new RouteStation(platform.area.getHexId(), position, platform.getName(), platform.getDwellTime());
	}
}
