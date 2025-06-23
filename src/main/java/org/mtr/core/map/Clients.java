package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.map.ClientsSchema;

public final class Clients extends ClientsSchema {

	public Clients(long currentMillis, ObjectArrayList<Client> clients) {
		super(currentMillis);
		clients.forEach(this.clients::add);
	}
}
