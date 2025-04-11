package org.mtr.core.operation;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.data.*;
import org.mtr.core.generated.operation.NearbyAreasResponseSchema;
import org.mtr.core.serializer.ReaderBase;

import javax.annotation.Nonnull;

public final class NearbyAreasResponse extends NearbyAreasResponseSchema {

	private final Data data;

	NearbyAreasResponse(Data data) {
		super();
		this.data = data;
	}

	public NearbyAreasResponse(ReaderBase readerBase, Data data) {
		super(readerBase);
		this.data = data;
		updateData(readerBase);
	}

	@Nonnull
	@Override
	protected Data depotsDataParameter() {
		return data;
	}

	@Nonnull
	@Override
	protected Data stationsDataParameter() {
		return data;
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
