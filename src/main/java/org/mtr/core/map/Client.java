package org.mtr.core.map;

import org.mtr.core.generated.map.ClientSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Client extends ClientSchema {

	public Client(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Client(String id, long x, long z, String stationId) {
		super(id, x, z, stationId, "", "", "");
	}

	public Client(Client client, String routeId, String routeStationId1, String routeStationId2) {
		super(client.id, client.x, client.z, client.stationId, routeId, routeStationId1, routeStationId2);
	}
}
