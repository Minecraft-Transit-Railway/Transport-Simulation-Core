package org.mtr.core.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public final class DepotDepartureTests {

	private Depot depot;

	@BeforeEach
	public void setUp() {
		final Simulator simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-depot"), false);
		depot = new Depot(TransportMode.TRAIN, simulator);
		depot.setName("Test Depot");
		depot.setCorners(new Position(-50, -50, -50), new Position(50, 50, 50));

		// Set frequencies for all hours via public API
		for (int i = 0; i < Utilities.HOURS_PER_DAY; i++) {
			depot.setFrequency(i, 2);
		}

		final Siding siding = new Siding(new Position(0, 0, 0), new Position(10, 0, 0), 0.5, TransportMode.TRAIN, simulator);
		siding.setName("Test Siding");
		simulator.sidings.add(siding);
		simulator.sync();
	}

	@Test
	public void testFrequencySetting() {
		depot.setFrequency(6, 10);
		assertEquals(10, depot.getFrequency(6), "Frequency at hour 6 should be 10");
		assertEquals(2, depot.getFrequency(0), "Frequency at hour 0 should still be default");
	}

	@Test
	public void testFrequencyOutOfBounds() {
		assertEquals(0, depot.getFrequency(-1), "Negative hour should return 0");
		assertEquals(0, depot.getFrequency(24), "Hour 24 should return 0");
	}

	@Test
	public void testUseRealTimeToggle() {
		assertFalse(depot.getUseRealTime(), "Default should be useRealTime = false");
		depot.setUseRealTime(true);
		assertTrue(depot.getUseRealTime(), "After setting, useRealTime should be true");
	}

	@Test
	public void testRepeatInfinitely() {
		assertFalse(depot.getRepeatInfinitely(), "Default should be repeatInfinitely = false");
		depot.setRepeatInfinitely(true);
		assertTrue(depot.getRepeatInfinitely(), "After setting, repeatInfinitely should be true");
	}

	@Test
	public void testCruisingAltitude() {
		assertEquals(256, depot.getCruisingAltitude(), "Default cruising altitude should be 256");
		depot.setCruisingAltitude(100);
		assertEquals(100, depot.getCruisingAltitude(), "After setting, cruising altitude should be 100");
	}

	@Test
	public void testGeneratedStatusDefaults() {
		assertEquals(Depot.GeneratedStatus.NONE, depot.getLastGeneratedStatus(), "Default generated status should be NONE");
	}

	@Test
	public void testRouteIds() {
		assertTrue(depot.getRouteIds().isEmpty(), "Default route ids should be empty");
	}

	@Test
	public void testGetRealTimeDepartures() {
		assertTrue(depot.getRealTimeDepartures().isEmpty(), "Default real-time departures should be empty");
	}
}
