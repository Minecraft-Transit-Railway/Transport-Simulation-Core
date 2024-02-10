package org.mtr.core.data;

import org.mtr.core.generated.data.ClientGroupSchema;
import org.mtr.core.integration.Integration;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.servlet.Webserver;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.util.UUID;
import java.util.function.Consumer;

public class ClientGroup extends ClientGroupSchema {

	private final int clientWebserverPort;

	public ClientGroup(int clientWebserverPort) {
		super();
		this.clientWebserverPort = clientWebserverPort;
	}

	public ClientGroup(ReaderBase readerBase, int clientWebserverPort) {
		super(readerBase);
		updateData(readerBase);
		this.clientWebserverPort = clientWebserverPort;
	}

	public double getUpdateRadius() {
		return updateRadius;
	}

	public void iterateClients(Consumer<Client> consumer) {
		clients.forEach(consumer);
	}

	public void sendToClient(JsonObject jsonObject) {
		Webserver.sendPostRequest(String.format("http://localhost:%s/mtr/api/socket", clientWebserverPort), jsonObject, null);
	}

	public void saveAndUpdate(ReaderBase readerBase) {
		final Object2ObjectArrayMap<UUID, Client> clientMap = new Object2ObjectArrayMap<>();
		clients.forEach(client -> clientMap.put(client.uuid, client));
		updateData(readerBase);
		clients.forEach(client -> clientMap.remove(client.uuid));
		clients.addAll(clientMap.values());
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
			sendToClient(updateObject);
		}
	}
}
