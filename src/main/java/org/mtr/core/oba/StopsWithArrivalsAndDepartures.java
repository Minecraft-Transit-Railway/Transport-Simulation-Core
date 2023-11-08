package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopsWithArrivalsAndDeparturesSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopsWithArrivalsAndDepartures extends StopsWithArrivalsAndDeparturesSchema {

	public StopsWithArrivalsAndDepartures(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
