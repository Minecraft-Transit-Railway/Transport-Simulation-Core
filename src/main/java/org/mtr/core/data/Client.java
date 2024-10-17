package org.mtr.core.data;

import org.mtr.core.generated.data.ClientSchema;
import org.mtr.core.operation.PlayerPresentResponse;
import org.mtr.core.operation.VehicleLiftResponse;
import org.mtr.core.operation.VehicleUpdate;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class Client extends ClientSchema {

	private long nextSendTime;

	private final LongAVLTreeSet existingVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepVehicleIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<VehicleUpdate> vehicleUpdates = new Long2ObjectAVLTreeMap<>();

	private final LongAVLTreeSet existingLiftIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepLiftIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<Lift> liftUpdates = new Long2ObjectAVLTreeMap<>();

	private final ObjectAVLTreeSet<String> existingRailIds = new ObjectAVLTreeSet<>();
	private final ObjectAVLTreeSet<String> keepRailIds = new ObjectAVLTreeSet<>();
	private final Object2ObjectAVLTreeMap<String, Rail> signalBlockUpdates = new Object2ObjectAVLTreeMap<>();

	public Client(String id) {
		super(id);
	}

	public Client(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	protected Position getDefaultPosition() {
		return new Position(0, 0, 0);
	}

	public Position getPosition() {
		return position;
	}

	public double getUpdateRadius() {
		return updateRadius;
	}

	public void setPositionAndUpdateRadius(Position position, long updateRadius) {
		this.position = position;
		this.updateRadius = updateRadius;
	}

	public void sendUpdates(Simulator simulator) {
		final long currentMillis = System.currentTimeMillis();
		if (currentMillis > nextSendTime) {
			nextSendTime = currentMillis + 100;
			final VehicleLiftResponse vehicleLiftResponse = new VehicleLiftResponse(clientId, simulator);
			final boolean hasUpdate1 = process(vehicleUpdates, existingVehicleIds, keepVehicleIds, vehicleLiftResponse::addVehicleToUpdate, vehicleLiftResponse::addVehicleToKeep);
			final boolean hasUpdate2 = process(liftUpdates, existingLiftIds, keepLiftIds, vehicleLiftResponse::addLiftToUpdate, vehicleLiftResponse::addLiftToKeep);
			final boolean hasUpdate3 = process(signalBlockUpdates, existingRailIds, keepRailIds, vehicleLiftResponse::addSignalBlockUpdate, railId -> {
			});

			if (hasUpdate1 || hasUpdate2 || hasUpdate3) {
				simulator.sendMessageS2C("vehicles-lifts", vehicleLiftResponse, responseObject -> new PlayerPresentResponse(new JsonReader(responseObject)).verify(simulator, clientId));
			}
		}
	}

	public void update(Vehicle vehicle, boolean needsUpdate, int pathUpdateIndex) {
		final long vehicleId = vehicle.getId();
		if (needsUpdate || !existingVehicleIds.contains(vehicleId)) {
			vehicleUpdates.put(vehicleId, new VehicleUpdate(vehicle, vehicle.vehicleExtraData.copy(pathUpdateIndex)));
			keepVehicleIds.remove(vehicleId);
		} else if (!vehicleUpdates.containsKey(vehicleId)) {
			keepVehicleIds.add(vehicleId);
		}
	}

	public void update(Lift lift, boolean needsUpdate) {
		final long liftId = lift.getId();
		if (needsUpdate || !existingLiftIds.contains(liftId)) {
			liftUpdates.put(liftId, lift);
			keepLiftIds.remove(liftId);
		} else if (!liftUpdates.containsKey(liftId)) {
			keepLiftIds.add(liftId);
		}
	}

	public void update(Rail rail, boolean needsUpdate) {
		final String railId = rail.getHexId();
		if (needsUpdate || !existingRailIds.contains(railId)) {
			signalBlockUpdates.put(railId, rail);
			keepRailIds.remove(railId);
		} else if (!signalBlockUpdates.containsKey(railId)) {
			keepRailIds.add(railId);
		}
	}

	private static <T, U extends SerializedDataBase> boolean process(Map<T, U> dataUpdates, Set<T> existingIds, Set<T> keepIds, Consumer<U> addDataToUpdate, Consumer<T> addDataToKeep) {
		dataUpdates.forEach((id, data) -> {
			addDataToUpdate.accept(data);
			existingIds.remove(id);
		});

		keepIds.forEach(id -> {
			addDataToKeep.accept(id);
			existingIds.remove(id);
		});

		// Has data to remove or has data to update
		final boolean hasUpdate = !existingIds.isEmpty() || !dataUpdates.isEmpty();

		existingIds.clear();
		existingIds.addAll(dataUpdates.keySet());
		existingIds.addAll(keepIds);

		dataUpdates.clear();
		keepIds.clear();
		return hasUpdate;
	}
}
