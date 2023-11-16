package org.mtr.core.operation;

import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleRidingEntity;
import org.mtr.core.generated.operation.UpdateVehicleRidingEntitiesSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;

public final class UpdateVehicleRidingEntities extends UpdateVehicleRidingEntitiesSchema {

	public UpdateVehicleRidingEntities(long sidingId, long vehicleId) {
		super(sidingId, vehicleId);
	}

	public UpdateVehicleRidingEntities(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void add(VehicleRidingEntity vehicleRidingEntity) {
		ridingEntities.add(vehicleRidingEntity);
	}

	public JsonObject update(Simulator simulator) {
		final Siding siding = simulator.sidingIdMap.get(sidingId);
		if (siding == null) {
			return new JsonObject();
		} else {
			siding.updateVehicleRidingEntities(vehicleId, ridingEntities);
			return Utilities.getJsonObjectFromData(this);
		}
	}
}
