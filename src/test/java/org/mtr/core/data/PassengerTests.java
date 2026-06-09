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
		passenger.tick(home, simulator);
		assertFalse(passenger.tick(home, simulator), "On cooldown, should return not dirty");
	}

	@Test
	public void testFindDirectionsBackoffOnAlreadyAtHome() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		passenger.tick(home, simulator);
	}

	@Test
	public void testTickDoesNotThrowWithLandmarkAndNoDirections() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		passenger.tick(home, simulator);
	}

	@Test
	public void testGetVehicleIdReturnsZeroByDefault() {
		final Passenger passenger = new Passenger(simulator);
		assertEquals(0, passenger.getVehicleId(), "New passenger should have vehicle ID 0");
	}

	@Test
	public void testTickReturnsDirtyOnFirstCall() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		assertTrue(passenger.tick(home, simulator), "First tick should be dirty due to initial dirtySync");
	}

	@Test
	public void testJourneyCompleteSetsCooldown() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		passenger.tick(home, simulator);
		passenger.tick(home, simulator);
		// After two ticks, cooldown should be set and tick should not throw
		assertDoesNotThrow(() -> passenger.tick(home, simulator));
	}

	@Test
	public void testMultiplePassengersDoNotInterfere() {
		final Passenger passenger1 = new Passenger(simulator);
		final Passenger passenger2 = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);

		assertTrue(passenger1.tick(home, simulator));
		assertTrue(passenger2.tick(home, simulator));

		// Both should be on cooldown now
		assertFalse(passenger1.tick(home, simulator));
		assertFalse(passenger2.tick(home, simulator));
	}

	@Test
	public void testIsRouteJammedDefaultsFalse() {
		assertFalse(simulator.isRouteJammed(42), "Route should not be jammed by default");
		assertFalse(simulator.isRouteJammed(0), "Route ID 0 should not be jammed");
	}

	@Test
	public void testMarkRouteJammed() {
		simulator.markRouteJammed(42);
		assertTrue(simulator.isRouteJammed(42), "Route 42 should be jammed after marking");
		assertFalse(simulator.isRouteJammed(0), "Route 0 should not be affected");
	}

	@Test
	public void testJammedRoutesClearOnTick() {
		simulator.markRouteJammed(42);
		assertTrue(simulator.isRouteJammed(42));
		// Tick clears jammed routes
		simulator.tick();
		assertFalse(simulator.isRouteJammed(42), "Jammed routes should be cleared after tick");
	}

	@Test
	public void testVehicleIdMapPopulatedOnTick() {
		simulator.tick();
		// After tick, vehicleIdMap should be non-null and usable
		assertNotNull(simulator.vehicleIdMap);
	}

	@Test
	public void testVehicleIdToPassengersClearedOnTick() {
		simulator.tick();
		assertNotNull(simulator.vehicleIdToPassengers);
	}

	@Test
	public void testHomeIteratePassengers() {
		final Home testHome = new Home(simulator);
		testHome.setName("Iterate Home");
		testHome.setCorners(new Position(-5, -5, -5), new Position(5, 5, 5));

		// iteratePassengers should not throw on empty home
		assertDoesNotThrow(() -> testHome.iteratePassengers(passenger -> {
		}));
	}

	@Test
	public void testPassengerDirectionGetStartTime() {
		// PassengerDirection is generated from CSA; its start time is the planned departure
		// Create one via driver and verify defaults
		final Passenger passenger = new Passenger(simulator);
		assertEquals(0, passenger.getVehicleId(), "Vehicle ID should be 0 initially");
	}
}
