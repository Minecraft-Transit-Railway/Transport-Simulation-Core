package org.mtr.core.data;

import org.mtr.core.generated.data.VehicleCarSchema;
import org.mtr.core.serializer.ReaderBase;

public final class VehicleCar extends VehicleCarSchema {

	public final boolean hasOneBogie;

	private static final int PASSENGERS_PER_SQUARE_METER = 2;

	public VehicleCar(String vehicleId, double length, double width, long capacity, double bogie1Position, double bogie2Position, double couplingPadding1, double couplingPadding2) {
		super(vehicleId, length, width, capacity, bogie1Position, bogie2Position, couplingPadding1, couplingPadding2);
		hasOneBogie = this.bogie1Position == this.bogie2Position;
	}

	public VehicleCar(ReaderBase readerBase) {
		super(readerBase);
		hasOneBogie = bogie1Position == bogie2Position;
		updateData(readerBase);
	}

	public String getVehicleId() {
		return vehicleId;
	}

	public double getLength() {
		return length;
	}

	public double getWidth() {
		return width;
	}

	public long getCapacity() {
		return capacity > 0 ? capacity : Math.round(length * width * PASSENGERS_PER_SQUARE_METER);
	}

	public double getBogie1Position() {
		return bogie1Position;
	}

	public double getBogie2Position() {
		return bogie2Position;
	}

	public double getTotalLength(boolean firstCar, boolean lastCar) {
		return getCouplingPadding1(firstCar) + length + getCouplingPadding2(lastCar);
	}

	double getCouplingPadding1(boolean firstCar) {
		return firstCar ? 0 : couplingPadding1;
	}

	double getCouplingPadding2(boolean lastCar) {
		return lastCar ? 0 : couplingPadding2;
	}
}
