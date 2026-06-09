package org.mtr.core.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public final class PassengerTests {

	private Simulator simulator;
	private Home home;

	@BeforeEach
	public void setUp() {
		simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-passenger"), false);
		home = new Home(simulator);
		home.setName("Test Home");
		home.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		final Landmark landmark = new Landmark(simulator);
		landmark.setName("Test Landmark");
		landmark.setCorners(new Position(100, -10, 100), new Position(120, 10, 120));
	}

	@Test
	public void testPassengerConstruction() {
		final Passenger passenger = new Passenger(simulator);
		assertNotNull(passenger);
		assertTrue(passenger.isValid());
	}

	@Test
	public void testIsValidReturnsTrue() {
		final Passenger passenger = new Passenger(simulator);
		assertTrue(passenger.isValid());
	}

	@Test
	public void testTickOnCooldownReturnsNotDirty() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		// First tick sets findDirectionsTime (due to initial dirtySync = true)
		passenger.tick(home, simulator);
		// Second tick should find findDirectionsTime in the future and return not dirty
		assertFalse(passenger.tick(home, simulator), "On cooldown, should return not dirty");
	}

	@Test
	public void testFindDirectionsBackoffOnAlreadyAtHome() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		// Should not throw; tests the findDirections(null) path when already at home
		passenger.tick(home, simulator);
	}

	@Test
	public void testTickDoesNotThrowWithLandmarkAndNoDirections() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		// With no landmarks in simulator, passenger should handle gracefully
		passenger.tick(home, simulator);
	}
}
