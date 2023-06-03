package org.mtr.core.client;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.Vehicle;
import org.mtr.core.generated.ClientSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Position;

import java.util.UUID;

public class Client extends ClientSchema {

	public final UUID uuid;
	public final ObjectAVLTreeSet<Vehicle> visibleVehicles = new ObjectAVLTreeSet<>();

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
}
