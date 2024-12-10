package org.mtr.core.map;

import org.mtr.core.generated.map.DeparturesSchema;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;

public final class Departures extends DeparturesSchema {

	public Departures(long currentMillis, Object2ObjectAVLTreeMap<String, Long2ObjectAVLTreeMap<LongArrayList>> departures) {
		super(currentMillis);
		departures.forEach((routeIdHex, departuresForRoute) -> this.departures.add(new DeparturesByRoute(routeIdHex, departuresForRoute)));
	}
}
