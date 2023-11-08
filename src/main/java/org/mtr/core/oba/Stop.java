package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Stop extends StopSchema {

	public Stop(String id, String code, String name, double lat, double lon, StopDirection direction) {
		super(id, code, name, "", lat, lon, "", 0, WheelchairBoarding.UNKNOWN, direction);
	}

	public Stop(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void addRouteId(String routeId) {
		routeIds.add(routeId);
	}
}
