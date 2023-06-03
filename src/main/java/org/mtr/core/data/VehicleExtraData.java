package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.generated.VehicleExtraDataSchema;
import org.mtr.core.serializers.ReaderBase;

public class VehicleExtraData extends VehicleExtraDataSchema {

	public final ObjectImmutableList<PathData> newPath;
	public final ObjectImmutableList<VehicleCar> newVehicleCars;

	protected VehicleExtraData(double railLength, double totalVehicleLength, long repeatIndex1, long repeatIndex2, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, double totalDistance, double defaultPosition, ObjectArrayList<VehicleCar> vehicleCars, ObjectArrayList<PathData> path) {
		super(railLength, totalVehicleLength, repeatIndex1, repeatIndex2, acceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition);
		this.path.clear();
		this.path.addAll(path);
		newPath = new ObjectImmutableList<>(path);
		this.vehicleCars.clear();
		this.vehicleCars.addAll(vehicleCars);
		newVehicleCars = new ObjectImmutableList<>(vehicleCars);
	}

	protected VehicleExtraData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		newPath = new ObjectImmutableList<>(path);
		newVehicleCars = new ObjectImmutableList<>(vehicleCars);
	}

	protected double getRailLength() {
		return railLength;
	}

	protected double getTotalVehicleLength() {
		return totalVehicleLength;
	}

	protected int getRepeatIndex1() {
		return (int) repeatIndex1;
	}

	protected int getRepeatIndex2() {
		return (int) repeatIndex2;
	}

	protected double getAcceleration() {
		return acceleration;
	}

	protected boolean getIsManualAllowed() {
		return isManualAllowed;
	}

	protected double getMaxManualSpeed() {
		return maxManualSpeed;
	}

	protected long getManualToAutomaticTime() {
		return manualToAutomaticTime;
	}

	protected double getTotalDistance() {
		return totalDistance;
	}

	protected double getDefaultPosition() {
		return defaultPosition;
	}
}
