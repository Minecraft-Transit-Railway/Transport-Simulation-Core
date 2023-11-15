package org.mtr.core.data;

import org.mtr.core.generated.data.ClientGroupSchema;
import org.mtr.core.integration.Integration;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.corundumstudio.socketio.SocketIOClient;
import org.mtr.libraries.com.google.gson.JsonObject;
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
		boolean update = false;
		final JsonObject updateObject = new JsonObject();

		for (final Client client : clients) {
			final Integration integration = client.getUpdates();
			if (integration != null) {
				updateObject.add(client.uuid.toString(), Utilities.getJsonObjectFromData(integration));
				update = true;
			}
		}

		if (update) {
			sendToClient.accept(updateObject);
		}
	}
}
