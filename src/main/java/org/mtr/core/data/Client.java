package org.mtr.core.data;

import org.mtr.core.generated.data.ClientSchema;
import org.mtr.core.integration.Integration;
import org.mtr.core.integration.VehicleUpdate;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;

import javax.annotation.Nullable;
import java.util.UUID;

public class Client extends ClientSchema {

	public final UUID uuid;
	private final LongAVLTreeSet existingVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepVehicleIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<VehicleUpdate> vehicleUpdates = new Long2ObjectAVLTreeMap<>();

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
		vehicleUpdates.forEach((vehicleId, vehicleUpdate) -> {
			integration.addVehicleToUpdate(vehicleUpdate);
			existingVehicleIds.remove(vehicleId.longValue());
		});

		keepVehicleIds.forEach(vehicleId -> {
			integration.addVehicleToKeep(vehicleId);
			existingVehicleIds.remove(vehicleId);
		});

		existingVehicleIds.forEach(integration::addVehicleToRemove);

		existingVehicleIds.clear();
		existingVehicleIds.addAll(vehicleUpdates.keySet());
		vehicleUpdates.clear();
		existingVehicleIds.addAll(keepVehicleIds);
		keepVehicleIds.clear();

		if (integration.noVehicleUpdates() && !forceUpdate) {
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
}
