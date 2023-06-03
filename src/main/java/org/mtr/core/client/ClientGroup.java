package org.mtr.core.client;

import com.corundumstudio.socketio.SocketIOClient;
import com.google.gson.JsonObject;
import org.mtr.core.generated.ClientGroupSchema;
import org.mtr.core.serializers.ReaderBase;
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
}
