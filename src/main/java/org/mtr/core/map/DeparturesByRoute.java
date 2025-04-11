package org.mtr.core.map;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.core.generated.map.DeparturesByRouteSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DeparturesByRoute extends DeparturesByRouteSchema {

	DeparturesByRoute(String id, Long2ObjectAVLTreeMap<LongArrayList> departuresForRoute) {
		super(id);
		departuresForRoute.forEach((deviation, departuresForDeviation) -> this.departures.add(new DeparturesByDeviation(deviation, departuresForDeviation)));
	}

	public DeparturesByRoute(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
