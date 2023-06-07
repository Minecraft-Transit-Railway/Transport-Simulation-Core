package org.mtr.core.client;

import com.corundumstudio.socketio.SocketIOClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.Vehicle;
import org.mtr.core.generated.ClientGroupSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Utilities;
import org.mtr.webserver.Webserver;

import java.util.function.Consumer;

public class ClientGroup extends ClientGroupSchema {

	private Consumer<JsonObject> sendToClient = jsonObject -> {
	};

	public ClientGroup() {
		super();
	}

	public ClientGroup(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public double getUpdateRadius() {
		return updateRadius;
	}

	public void setSendToClient(Webserver webserver, SocketIOClient socketIOClient, String channel) {
		sendToClient = jsonObject -> webserver.sendSocketEvent(socketIOClient, channel, jsonObject);
	}

	public void iterateClients(Consumer<Client> consumer) {
		clients.forEach(consumer);
	}

	public void sendToClient(JsonObject jsonObject) {
		sendToClient.accept(jsonObject);
	}

	public void tick() {
		final boolean[] update = {false};
		final JsonObject jsonObject = new JsonObject();

		clients.forEach(client -> {
			final ObjectAVLTreeSet<Vehicle> vehiclesToUpdate = new ObjectAVLTreeSet<>();
			final LongAVLTreeSet vehicleIdsToRemove = new LongAVLTreeSet();
			client.tick(vehiclesToUpdate, vehicleIdsToRemove);

			if (!vehiclesToUpdate.isEmpty() || !vehicleIdsToRemove.isEmpty()) {
				update[0] = true;
				final JsonArray updateArray = new JsonArray();
				vehiclesToUpdate.forEach(vehicle -> {
					final JsonObject vehicleObject = new JsonObject();
					vehicleObject.add("vehicle", Utilities.getJsonObjectFromData(vehicle));
					vehicleObject.add("data", Utilities.getJsonObjectFromData(vehicle.vehicleExtraData));
					updateArray.add(vehicleObject);
				});
				final JsonArray removeArray = new JsonArray();
				vehicleIdsToRemove.forEach(removeArray::add);
				final JsonObject clientObject = new JsonObject();
				clientObject.add("update", updateArray);
				clientObject.add("remove", removeArray);
				jsonObject.add(client.uuid.toString(), clientObject);
			}
		});

		if (update[0]) {
			sendToClient.accept(jsonObject);
		}
	}
}
