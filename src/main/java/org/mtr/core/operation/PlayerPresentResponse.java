package org.mtr.core.operation;

import org.mtr.core.Main;
import org.mtr.core.generated.operation.PlayerPresentResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public class PlayerPresentResponse extends PlayerPresentResponseSchema {

	public PlayerPresentResponse(boolean playerPresent) {
		super(playerPresent);
	}

	public PlayerPresentResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void verify(Simulator simulator, String clientId) {
		if (!playerPresent) {
			simulator.run(() -> {
				simulator.clients.remove(clientId);
				Main.LOGGER.info(String.format("Removing player %s", clientId));
			});
		}
	}
}
