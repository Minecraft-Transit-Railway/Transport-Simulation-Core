package org.mtr.core.operation;

import org.mtr.core.data.Client;
import org.mtr.core.generated.operation.DepotGenerationResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.function.Consumer;

public final class DepotGenerationResponse extends DepotGenerationResponseSchema {

	public DepotGenerationResponse() {
		super();
	}

	public DepotGenerationResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void addDepotGenerationUpdate(DepotGenerationUpdate depotGenerationUpdate) {
		depotGenerationUpdates.add(depotGenerationUpdate);
	}

	public void trySend(Object2ObjectOpenHashMap<String, Client> clients, Runnable runnable) {
		if (!clients.isEmpty() && !depotGenerationUpdates.isEmpty()) {
			clientIds.addAll(clients.keySet());
			runnable.run();
			clientIds.clear();
			depotGenerationUpdates.clear();
		}
	}

	public void iterateClientIds(Consumer<String> consumer) {
		clientIds.forEach(consumer);
	}

	public void iterateDepotGenerationUpdates(Consumer<DepotGenerationUpdate> consumer) {
		depotGenerationUpdates.forEach(consumer);
	}
}
