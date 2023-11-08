package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopsForRouteSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopsForRoute extends StopsForRouteSchema {

	public StopsForRoute(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
