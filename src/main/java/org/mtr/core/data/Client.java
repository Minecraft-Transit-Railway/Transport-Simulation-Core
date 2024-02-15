package org.mtr.core.data;

import org.mtr.core.generated.data.ClientSchema;
import org.mtr.core.integration.Response;
import org.mtr.core.operation.VehicleLiftResponse;
import org.mtr.core.operation.VehicleUpdate;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.RequestHelper;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.io.netty.handler.codec.http.HttpResponseStatus;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class Client extends ClientSchema {

	private final LongAVLTreeSet existingVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepVehicleIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<VehicleUpdate> vehicleUpdates = new Long2ObjectAVLTreeMap<>();
	private final LongAVLTreeSet existingLiftIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepLiftIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<Lift> liftUpdates = new Long2ObjectAVLTreeMap<>();

	private static final RequestHelper REQUEST_HELPER = new RequestHelper(false);

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

	public void sendUpdates(Simulator simulator, int clientWebserverPort) {
		final VehicleLiftResponse vehicleLiftResponse = new VehicleLiftResponse(clientId, simulator);
		process(vehicleUpdates, existingVehicleIds, keepVehicleIds, vehicleLiftResponse::addVehicleToUpdate, vehicleLiftResponse::addVehicleToKeep, vehicleLiftResponse::addVehicleToRemove);
		process(liftUpdates, existingLiftIds, keepLiftIds, vehicleLiftResponse::addLiftToUpdate, vehicleLiftResponse::addLiftToKeep, vehicleLiftResponse::addLiftToRemove);

		if (vehicleLiftResponse.hasUpdate()) {
			REQUEST_HELPER.sendPostRequest("http://localhost:" + clientWebserverPort, new Response(HttpResponseStatus.OK.code(), System.currentTimeMillis(), "Success", Utilities.getJsonObjectFromData(vehicleLiftResponse)).getJson(), null);
		}
	}

	public void update(Vehicle vehicle, boolean needsUpdate, int pathUpdateIndex) {
		final long vehicleId = vehicle.getId();
		if (needsUpdate || !existingVehicleIds.contains(vehicleId)) {
			vehicleUpdates.put(vehicleId, new VehicleUpdate(vehicle, vehicle.vehicleExtraData.copy(pathUpdateIndex)));
		} else {
			keepVehicleIds.add(vehicleId);
		}
	}

	public void update(Lift lift, boolean needsUpdate) {
		final long liftId = lift.getId();
		if (needsUpdate || !existingLiftIds.contains(liftId)) {
			liftUpdates.put(liftId, lift);
		} else {
			keepLiftIds.add(liftId);
		}
	}

	private static <T extends SerializedDataBase> void process(Long2ObjectAVLTreeMap<T> dataUpdates, LongAVLTreeSet existingIds, LongAVLTreeSet keepIds, Consumer<T> addDataToUpdate, LongConsumer addDataToKeep, LongConsumer addDataToRemove) {
		dataUpdates.forEach((id, data) -> {
			addDataToUpdate.accept(data);
			existingIds.remove(id.longValue());
		});

		keepIds.forEach(id -> {
			addDataToKeep.accept(id);
			existingIds.remove(id);
		});

		existingIds.forEach(addDataToRemove::accept);

		existingIds.clear();
		existingIds.addAll(dataUpdates.keySet());
		dataUpdates.clear();
		existingIds.addAll(keepIds);
		keepIds.clear();
	}
}
