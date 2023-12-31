package org.mtr.core.operation;

import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Depot;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.data.Station;
import org.mtr.core.generated.operation.NearbyAreasResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

public final class NearbyAreasResponse extends NearbyAreasResponseSchema {

	NearbyAreasResponse() {
		super();
	}

	public NearbyAreasResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public ObjectImmutableList<Station> getStations() {
		return new ObjectImmutableList<>(stations);
	}

	public ObjectImmutableList<Depot> getDepots() {
		return new ObjectImmutableList<>(depots);
	}

	<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void add(T area) {
		if (area instanceof Station) {
			stations.add((Station) area);
		} else if (area instanceof Depot) {
			depots.add((Depot) area);
		}
	}
}
