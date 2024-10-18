package org.mtr.core.operation;

import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleRidingEntity;
import org.mtr.core.generated.operation.UpdateVehicleRidingEntitiesSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import javax.annotation.Nullable;

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

	@Nullable
	public UpdateVehicleRidingEntities update(Simulator simulator) {
		final Siding siding = simulator.sidingIdMap.get(sidingId);
		if (siding == null) {
			return null;
		} else {
			siding.updateVehicleRidingEntities(vehicleId, ridingEntities);
			return this;
		}
	}
}
