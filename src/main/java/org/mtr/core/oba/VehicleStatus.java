package org.mtr.core.oba;

import org.mtr.core.generated.oba.VehicleStatusSchema;
import org.mtr.core.serializer.ReaderBase;

public final class VehicleStatus extends VehicleStatusSchema {

	public VehicleStatus(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
