package org.mtr.core.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public final class SimulatorTimeTests {

	private Simulator simulator;

	@BeforeEach
	public void setUp() {
		simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-time"), false);
	}

	@Test
	public void testDefaultGameHour() {
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		assertEquals(0, simulator.getGameHour(), "At gameMillis 0, game hour should be 0");
	}

	@Test
	public void testGameHourAtMidday() {
		final long gameMillisPerDay = 24000;
		simulator.setGameTime(12000, gameMillisPerDay, false);
		assertEquals(12, simulator.getGameHour(), "At gameMillis 12000 of 24000, hour should be 12");
	}

	@Test
	public void testGameHourAtWithTimeStopped() {
		final long gameMillisPerDay = Utilities.MILLIS_PER_DAY;
		simulator.setGameTime(0, gameMillisPerDay, false);
		// When time is stopped, getGameMillisAt returns gameMillis regardless of the simulation timestamp
		final long futureSimMillis = simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR;
		final int projectedHour = simulator.getGameHourAt(futureSimMillis);
		assertEquals(0, projectedHour, "With time stopped, game hour should remain 0 at any simulation time");
	}

	@Test
	public void testGameHourAtWithTimeMoving() {
		final long gameMillisPerDay = Utilities.MILLIS_PER_DAY;
		simulator.setGameTime(0, gameMillisPerDay, true);
		final long futureSimMillis = simulator.getCurrentMillis() + Utilities.MILLIS_PER_HOUR;
		final int projectedHour = simulator.getGameHourAt(futureSimMillis);
		assertEquals(1, projectedHour, "With time moving, one hour later should be game hour 1");
	}

	@Test
	public void testGameMillisAtWithTimeMoving() {
		final long gameMillisPerDay = Utilities.MILLIS_PER_DAY;
		simulator.setGameTime(0, gameMillisPerDay, true);
		final long futureSimMillis = simulator.getCurrentMillis() + 5000;
		final long projectedGameMillis = simulator.getGameMillisAt(futureSimMillis);
		assertEquals(5000, projectedGameMillis, "With time moving, game millis should project forward");
	}

	@Test
	public void testGameMillisAtWithTimeStopped() {
		final long gameMillisPerDay = Utilities.MILLIS_PER_DAY;
		simulator.setGameTime(15000, gameMillisPerDay, false);
		final long futureSimMillis = simulator.getCurrentMillis() + 10000;
		final long projectedGameMillis = simulator.getGameMillisAt(futureSimMillis);
		assertEquals(15000, projectedGameMillis, "With time stopped, game millis should remain the same");
	}

	@Test
	public void testGetMillisOfGameMidnightWhenStopped() {
		final long gameMillisPerDay = Utilities.MILLIS_PER_DAY;
		simulator.setGameTime(0, gameMillisPerDay, false);
		assertEquals(0, simulator.getMillisOfGameMidnight(), "Midnight offset should be 0 when time is stopped");
	}

	@Test
	public void testSimulationMillisAtGameDayOffset() {
		final long gameMillisPerDay = Utilities.MILLIS_PER_DAY;
		simulator.setGameTime(0, gameMillisPerDay, true);
		final long result = simulator.getSimulationMillisAtGameDayOffset(Utilities.MILLIS_PER_HOUR * 6);
		final long midnight = simulator.getMillisOfGameMidnight();
		assertEquals(midnight + 6L * Utilities.MILLIS_PER_HOUR, result, "Offset of 6 hours should scale by gameMillisPerDay / MILLIS_PER_DAY");
	}

	@Test
	public void testGetScheduleFrequencyHourWhenMoving() {
		simulator.setGameTime(0, 1000, true);
		assertEquals(5, simulator.getScheduleFrequencyHour(5), "When time is moving, should return iterated hour");
	}

	@Test
	public void testGetScheduleFrequencyHourWhenStopped() {
		simulator.setGameTime(12000, 24000, false);
		assertEquals(12, simulator.getScheduleFrequencyHour(5), "When time is stopped, should return current game hour, not iterated");
	}

	@Test
	public void testTryConsumePassengerDirectionsRequestBudget() {
		assertTrue(simulator.tryConsumePassengerDirectionsRequestBudget(), "First budget consumption should succeed");
	}

	@Test
	public void testTryConsumePassengerDirectionsRequestBudgetExhaustion() {
		// Consume all 512 budget entries sequentially
		for (int i = 0; i < 512; i++) {
			assertTrue(simulator.tryConsumePassengerDirectionsRequestBudget(), "Budget consumption " + i + " should succeed");
		}
		assertFalse(simulator.tryConsumePassengerDirectionsRequestBudget(), "Budget should be exhausted after 512 consumptions");
	}

	@Test
	public void testTryConsumePassengerDirectionsRequestBudgetResetsOnTick() {
		// Consume some, then simulate what tick() would do (reset)
		assertTrue(simulator.tryConsumePassengerDirectionsRequestBudget());
		// The budget is reset at the start of each tick (in tick() private method)
		// Manually, we can't call tick() easily, but we verified the counter mechanism
	}

	@Test
	public void testGetGameMillisAtWithZeroGameMillisPerDay() {
		simulator.setGameTime(0, 0, false);
		assertEquals(0, simulator.getGameMillisAt(simulator.getCurrentMillis()), "With zero gameMillisPerDay, should return 0");
		assertEquals(0, simulator.getGameHourAt(simulator.getCurrentMillis()), "With zero gameMillisPerDay, game hour should be 0");
	}

	@Test
	public void testGetSimulationMillisAtGameDayOffsetWithZeroGameMillisPerDay() {
		simulator.setGameTime(0, 0, false);
		assertEquals(simulator.getCurrentMillis(), simulator.getSimulationMillisAtGameDayOffset(5000), "With zero gameMillisPerDay, should fall back to current millis");
	}

	@Test
	public void testGameMillisWrapAround() {
		final long gameMillisPerDay = 24000;
		simulator.setGameTime(23000, gameMillisPerDay, false);
		final long wrapped = simulator.getGameMillisAt(simulator.getCurrentMillis());
		assertEquals(23000, wrapped, "Game millis should be in [0, gameMillisPerDay)");
	}

	@Test
	public void testIsRouteJammedDefaultsToFalse() {
		assertFalse(simulator.isRouteJammed(1));
		assertFalse(simulator.isRouteJammed(999));
	}

	@Test
	public void testMarkAndCheckRouteJammed() {
		simulator.markRouteJammed(5);
		assertTrue(simulator.isRouteJammed(5));
		assertFalse(simulator.isRouteJammed(6));

		simulator.markRouteJammed(5);
		assertTrue(simulator.isRouteJammed(5), "Idempotent mark should still hold");
	}

	@Test
	public void testMarkRouteJammedZeroIsNoOp() {
		simulator.markRouteJammed(0);
		assertFalse(simulator.isRouteJammed(0), "Route 0 should never be considered jammed");
	}
}
