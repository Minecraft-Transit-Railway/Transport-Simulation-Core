package org.mtr.core.operation;

import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Position;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.generated.operation.NearbyAreasRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

public final class NearbyAreasRequest<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> extends NearbyAreasRequestSchema {

	public NearbyAreasRequest(Position position, long radius) {
		super(position, radius);
	}

	public NearbyAreasRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public NearbyAreasResponse query(Simulator simulator, ObjectArraySet<T> areaSet) {
		final NearbyAreasResponse nearbyAreasResponse = new NearbyAreasResponse(simulator);
		areaSet.forEach(area -> {
			if (area.inArea(position, radius)) {
				nearbyAreasResponse.add(area);
			}
		});
		return nearbyAreasResponse;
	}
}
