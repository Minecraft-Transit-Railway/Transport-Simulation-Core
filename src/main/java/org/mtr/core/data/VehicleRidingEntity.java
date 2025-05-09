package org.mtr.core.data;

import org.mtr.core.generated.data.VehicleRidingEntitySchema;
import org.mtr.core.serializer.ReaderBase;

import java.util.UUID;

public class VehicleRidingEntity extends VehicleRidingEntitySchema {

	public final UUID uuid;

	public VehicleRidingEntity(UUID uuid, long ridingCar, double x, double y, double z, boolean isOnGangway, boolean isDriver, boolean manualAccelerate, boolean manualBrake, boolean manualToggleDoors, boolean manualToggleAto) {
		super(uuid.toString(), ridingCar, x, y, z, isOnGangway, isDriver, manualAccelerate, manualBrake, manualToggleDoors, manualToggleAto);
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

	public boolean isDriver() {
		return isDriver;
	}

	public boolean manualAccelerate() {
		return manualAccelerate;
	}

	public boolean manualBrake() {
		return manualBrake;
	}

	public boolean manualToggleDoors() {
		return manualToggleDoors;
	}

	public boolean manualToggleAto() {
		return manualToggleAto;
	}
}
