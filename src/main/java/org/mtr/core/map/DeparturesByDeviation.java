package org.mtr.core.map;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.core.generated.map.DeparturesByDeviationSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DeparturesByDeviation extends DeparturesByDeviationSchema {

	public DeparturesByDeviation(long deviation, LongArrayList departuresForDeviation) {
		super(deviation);
		departures.addAll(departuresForDeviation);
	}

	public DeparturesByDeviation(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
