package org.mtr.core.integration;

import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.generated.integration.VehicleUpdateSchema;
import org.mtr.core.serializer.ReaderBase;

public final class VehicleUpdate extends VehicleUpdateSchema {

	public VehicleUpdate(Vehicle vehicle, VehicleExtraData data) {
		super(vehicle, data);
	}

	public VehicleUpdate(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Vehicle getVehicle() {
		return vehicle;
	}

	public VehicleExtraData getVehicleExtraData() {
		return data;
	}
}
