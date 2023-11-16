package org.mtr.core.data;

import org.mtr.core.generated.data.VehicleRidingEntitySchema;
import org.mtr.core.serializer.ReaderBase;

import java.util.UUID;

public class VehicleRidingEntity extends VehicleRidingEntitySchema {

	public UUID uuid;

	public VehicleRidingEntity(UUID uuid, long ridingCar, double x, double y, double z, boolean isOnGangway) {
		super(uuid.toString(), ridingCar, x, y, z, isOnGangway);
		this.uuid = uuid;
	}

	public VehicleRidingEntity(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		uuid = UUID.fromString(clientId);
	}

	public long getRidingCar() {
		return ridingCar;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public boolean getIsOnGangway() {
		return isOnGangway;
	}

	public boolean isOnVehicle() {
		return ridingCar >= 0;
	}
}
