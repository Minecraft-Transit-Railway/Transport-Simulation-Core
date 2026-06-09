package org.mtr.core.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;

import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class ClientTests {

	private Simulator simulator;

	@BeforeEach
	public void setUp() {
		simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-client"), false);
	}

	@Test
	public void testClientConstruction() {
		final UUID uuid = UUID.randomUUID();
		final Client client = new Client(uuid);
		assertNotNull(client);
		assertEquals(uuid, client.uuid);
	}

	@Test
	public void testDefaultPosition() {
		final Client client = new Client(UUID.randomUUID());
		final Position position = client.getPosition();
		assertNotNull(position);
		assertEquals(0, position.getX());
		assertEquals(0, position.getY());
		assertEquals(0, position.getZ());
	}

	@Test
	public void testSetPositionAndUpdateRadius() {
		final Client client = new Client(UUID.randomUUID());
		final Position position = new Position(100, 64, -200);
		client.setPositionAndUpdateRadius(position, 500);
		assertEquals(position, client.getPosition());
		assertEquals(500, client.getUpdateRadius());
	}

	@Test
	public void testPassengerUpdateDoesNotThrow() {
		final Client client = new Client(UUID.randomUUID());
		final Passenger passenger = new Passenger(simulator);
		client.update(passenger, true);
	}

	@Test
	public void testPassengerUpdateKeepAliveDoesNotThrow() {
		final Client client = new Client(UUID.randomUUID());
		final Passenger passenger = new Passenger(simulator);
		// Test the "keep alive" path: second update with needsUpdate=false
		client.update(passenger, true);
		client.update(passenger, false);
	}
}
