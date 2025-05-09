package org.mtr.core.operation;

import org.mtr.core.data.Data;
import org.mtr.core.data.Lift;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.operation.VehicleLiftResponseSchema;
import org.mtr.core.serializer.ReaderBase;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class VehicleLiftResponse extends VehicleLiftResponseSchema {

	public final UUID uuid;
	private final Data data;

	public VehicleLiftResponse(UUID uuid, Data data) {
		super(uuid.toString());
		this.uuid = uuid;
		this.data = data;
	}

	public VehicleLiftResponse(ReaderBase readerBase, Data data) {
		super(readerBase);
		this.data = data;
		updateData(readerBase);
		uuid = UUID.fromString(clientId);
	}

	@Nonnull
	@Override
	protected Data liftsToUpdateDataParameter() {
		return data;
	}

	public void iterateVehiclesToUpdate(Consumer<VehicleUpdate> consumer) {
		vehiclesToUpdate.forEach(consumer);
	}

	public void iterateVehiclesToKeep(LongConsumer consumer) {
		vehiclesToKeep.forEach(consumer);
	}

	public void iterateLiftsToUpdate(Consumer<Lift> consumer) {
		liftsToUpdate.forEach(consumer);
	}

	public void iterateLiftsToKeep(LongConsumer consumer) {
		liftsToKeep.forEach(consumer);
	}

	public void iterateSignalBlockUpdates(Consumer<SignalBlockUpdate> consumer) {
		signalBlockUpdates.forEach(consumer);
	}

	public void addVehicleToUpdate(VehicleUpdate vehicleUpdate) {
		vehiclesToUpdate.add(vehicleUpdate);
	}

	public void addVehicleToKeep(long vehicleId) {
		vehiclesToKeep.add(vehicleId);
	}

	public void addLiftToUpdate(Lift lift) {
		liftsToUpdate.add(lift);
	}

	public void addLiftToKeep(long liftId) {
		liftsToKeep.add(liftId);
	}

	public void addSignalBlockUpdate(Rail rail) {
		signalBlockUpdates.add(new SignalBlockUpdate(rail));
	}
}
