package org.mtr.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.core.data.Vehicle;
import org.mtr.core.generated.ClientSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.util.UUID;

public class Client extends ClientSchema {

	public final UUID uuid;
	private final LongAVLTreeSet existingVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepVehicleIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<JsonObject> vehicleUpdates = new Long2ObjectAVLTreeMap<>();

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

	public JsonObject getUpdates() {
		final JsonArray updateArray = new JsonArray();
		vehicleUpdates.forEach((vehicleId, jsonObject) -> {
			updateArray.add(jsonObject);
			existingVehicleIds.remove(vehicleId.longValue());
		});

		final JsonArray keepArray = new JsonArray();
		keepVehicleIds.forEach(vehicleId -> {
			keepArray.add(vehicleId);
			existingVehicleIds.remove(vehicleId);
		});

		final JsonArray removeArray = new JsonArray();
		existingVehicleIds.forEach(removeArray::add);

		existingVehicleIds.clear();
		existingVehicleIds.addAll(vehicleUpdates.keySet());
		vehicleUpdates.clear();
		existingVehicleIds.addAll(keepVehicleIds);
		keepVehicleIds.clear();

		if (updateArray.isEmpty() && removeArray.isEmpty() && !forceUpdate) {
			return null;
		} else {
			final JsonObject jsonObject = new JsonObject();
			jsonObject.add("update", updateArray);
			jsonObject.add("keep", keepArray);
			jsonObject.add("remove", removeArray);
			forceUpdate = false;
			return jsonObject;
		}
	}

	public void update(Vehicle vehicle, boolean needsUpdate, int pathUpdateIndex) {
		final long vehicleId = vehicle.getId();
		if (needsUpdate || !existingVehicleIds.contains(vehicleId)) {
			final JsonObject jsonObject = new JsonObject();
			jsonObject.add("vehicle", Utilities.getJsonObjectFromData(vehicle));
			jsonObject.add("data", Utilities.getJsonObjectFromData(vehicle.vehicleExtraData.copy(pathUpdateIndex)));
			vehicleUpdates.put(vehicleId, jsonObject);
		} else {
			keepVehicleIds.add(vehicleId);
		}
	}
}
