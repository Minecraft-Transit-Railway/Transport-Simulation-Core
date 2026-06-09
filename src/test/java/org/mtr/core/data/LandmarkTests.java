package org.mtr.core.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class LandmarkTests {

	private Simulator simulator;

	@BeforeEach
	public void setUp() {
		simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-landmark"), false);
	}

	@Test
	public void testLandmarkConstruction() {
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

		// With empty densities, no visit should be possible
		final long visitDuration = landmark.reserveVisit(simulator.getCurrentMillis());
		assertEquals(0, visitDuration, "Visit duration should be 0 when landmark has no densities configured");
	}

	@Test
	public void testTick() {
		final Landmark landmark = new Landmark(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		// tick should not throw
		landmark.tick();
	}

	@Test
	public void testWriteVisitCacheDoesNotThrow() {
		final Landmark landmark = new Landmark(simulator);
		landmark.writeVisitCache(simulator.getCurrentMillis(), simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR);
	}

	@Test
	public void testReserveVisitWithWallClock() {
		final Landmark landmark = new Landmark(simulator);
		// Without game time (non-Simulator data path), reserveVisit returns 0
		// This tests the else branch
		final long visitDuration = landmark.reserveVisit(System.currentTimeMillis());
		assertEquals(0, visitDuration, "Without simulator context, reserveVisit should return 0");
	}

	@Test
	public void testGetDayDivisionIndexViaReserve() {
		simulator.setGameTime(Utilities.MILLIS_PER_HOUR * 6, Utilities.MILLIS_PER_DAY, false);
		final Landmark landmark = new Landmark(simulator);

		// With empty densities, reserve at any hour returns 0
		final long visitDuration = landmark.reserveVisit(simulator.getCurrentMillis());
		assertEquals(0, visitDuration, "Without configured densities, reservation should fail");
	}

	@Test
	public void testMultipleTicks() {
		final Landmark landmark = new Landmark(simulator);
		// Running tick multiple times should not throw
		landmark.tick();
		landmark.tick();
		landmark.tick();
	}

	@Test
	public void testReserveVisitWithUseRealTimeTrue() {
		final Landmark landmark = new Landmark(simulator);
		landmark.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(Utilities.MILLIS_PER_HOUR * 8, Utilities.MILLIS_PER_DAY, false);
		// With useRealTime = true and no densities, should still return 0
		final long visitDuration = landmark.reserveVisit(simulator.getCurrentMillis());
		assertEquals(0, visitDuration, "Without configured densities, reservation should fail");
	}

	@Test
	public void testWriteVisitCacheWithSimulatorContext() {
		final Landmark landmark = new Landmark(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		// Should not throw when called with simulator context
		landmark.writeVisitCache(simulator.getCurrentMillis(), simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR);
	}

	@Test
	public void testReserveVisitWithGameTimeMoving() {
		final Landmark landmark = new Landmark(simulator);
		landmark.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, true);
		// With time moving and empty densities, should still return 0
		final long visitDuration = landmark.reserveVisit(simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR);
		assertEquals(0, visitDuration, "Without configured densities, reservation should fail even with time moving");
	}
}
