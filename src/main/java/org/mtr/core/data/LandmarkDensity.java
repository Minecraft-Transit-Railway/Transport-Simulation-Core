package org.mtr.core.data;

import org.mtr.core.generated.data.LandmarkDensitySchema;
import org.mtr.core.serializer.ReaderBase;

public final class LandmarkDensity extends LandmarkDensitySchema {

	public LandmarkDensity(long startTime, long endTime, boolean isRealTime, long minDensity, long maxDensity, long minStayDuration, long maxStayDuration) {
		super(startTime, endTime, isRealTime, minDensity, maxDensity, minStayDuration, maxStayDuration);
	}

	public LandmarkDensity(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
