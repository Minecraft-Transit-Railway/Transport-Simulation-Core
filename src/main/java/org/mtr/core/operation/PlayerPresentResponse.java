package org.mtr.core.operation;

import lombok.extern.log4j.Log4j2;
import org.mtr.core.generated.operation.PlayerPresentResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import java.util.UUID;

/**
 * Server-to-client confirmation that a given player UUID is currently considered "present" in
 * a particular dimension. Used by {@link Simulator} to evict client records when a player has
 * been moved to a different dimension since the last update.
 */
@Log4j2
public class PlayerPresentResponse extends PlayerPresentResponseSchema {

	/** Construct a response naming the dimension the player is currently in. */
	public PlayerPresentResponse(String playerDimension) {
		super(playerDimension);
	}

	/** Deserialisation constructor used by the wire layer. */
	public PlayerPresentResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	/**
	 * If the player is no longer in {@code simulator}'s dimension, schedule eviction of any
	 * matching {@link org.mtr.core.data.Client} record on the simulator thread.
	 */
	public void verify(Simulator simulator, UUID uuid) {
		if (!playerDimension.equals(simulator.dimension)) {
			simulator.run(() -> {
				if (simulator.clients.removeIf(client -> client.uuid.equals(uuid))) {
					log.info("Removing player {}", uuid);
				}
			});
		}
	}
}
