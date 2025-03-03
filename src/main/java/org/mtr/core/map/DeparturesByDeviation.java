package org.mtr.core.map;

import org.mtr.core.generated.map.DeparturesByDeviationSchema;
import org.mtr.core.serializer.ReaderBase;
import it.unimi.dsi.fastutil.longs.LongArrayList;

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
