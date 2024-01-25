package org.mtr.core.data;

import org.mtr.core.generated.data.ClientSchema;
import org.mtr.core.integration.Integration;
import org.mtr.core.integration.VehicleUpdate;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class Client extends ClientSchema {

	public final UUID uuid;
	private final LongAVLTreeSet existingVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepVehicleIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<VehicleUpdate> vehicleUpdates = new Long2ObjectAVLTreeMap<>();
	private final LongAVLTreeSet existingLiftIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepLiftIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<Lift> liftUpdates = new Long2ObjectAVLTreeMap<>();

	public Client(UUID uuid) {
		super(uuid.toString());
		this.uuid = uuid;
	}

	public Client(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		this.uuid = UUID.fromString(clientId);
	}

	@Override
	protected Position getDefaultPosition() {
		return new Position(0, 0, 0);
	}

	public Position getPosition() {
		return position;
	}

	@Nullable
	public Integration getUpdates() {
		final Integration integration = new Integration(new Data());
		process(vehicleUpdates, existingVehicleIds, keepVehicleIds, integration::addVehicleToUpdate, integration::addVehicleToKeep, integration::addVehicleToRemove);
		process(liftUpdates, existingLiftIds, keepLiftIds, integration::addLiftToUpdate, integration::addLiftToKeep, integration::addLiftToRemove);

		if (integration.noVehicleOrLiftUpdates() && !forceUpdate) {
			return null;
		} else {
			forceUpdate = false;
			return integration;
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
