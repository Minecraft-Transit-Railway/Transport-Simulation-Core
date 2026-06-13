package org.mtr.core.data;

import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public final class LandmarkTests {

	private Simulator simulator;

	@BeforeEach
	public void setUp() {
		simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-landmark"), false);
	}

	@Test
	public void testConstruction() {
		final Landmark landmark = new Landmark(simulator);
		landmark.setName("Test Landmark");
		assertNotNull(landmark);
		assertEquals("Test Landmark", landmark.getName());
	}

	@Test
	public void testReserveVisitReturnsZeroWhenDensitiesEmpty() {
		final Landmark landmark = new Landmark(simulator);
		landmark.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		assertEquals(0, landmark.reserveVisit(simulator.getCurrentMillis()), "Visit duration should be 0 when landmark has no densities configured");
	}

	@Test
	public void testWriteAndEndVisitLifecycle() {
		final Landmark landmark = new Landmark(simulator);
		landmark.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);

		for (int hour = 0; hour < 24; hour++) {
			landmark.setDensity(hour, 1);
		}

		final long startTime = simulator.getCurrentMillis();
		final long endTime = startTime + Utilities.MILLIS_PER_HOUR;

		// Fill slots with one visit
		landmark.writeVisitCache(startTime, endTime);
		landmark.tick();

		// With density 1 and slot occupied, reserve should fail
		assertEquals(0, landmark.reserveVisit(startTime), "Slot should be full after writeVisitCache");

		// Free the slots
		landmark.endVisit(startTime, endTime);
		landmark.tick();

		// Now reserve should succeed
		assertTrue(landmark.reserveVisit(startTime) > 0, "Slot should be free after endVisit");
	}

	@Test
	public void testWriteVisitCacheDoesNotThrow() {
		final Landmark landmark = new Landmark(simulator);
		assertDoesNotThrow(() -> landmark.writeVisitCache(simulator.getCurrentMillis(), simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR));
	}

	@Test
	public void testEndVisitDoesNotThrow() {
		final Landmark landmark = new Landmark(simulator);
		assertDoesNotThrow(() -> landmark.endVisit(simulator.getCurrentMillis(), simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR));
	}

	@Test
	public void testMultipleTicksDoNotThrow() {
		final Landmark landmark = new Landmark(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		assertDoesNotThrow(() -> {
			landmark.tick();
			landmark.tick();
			landmark.tick();
		});
	}
}
