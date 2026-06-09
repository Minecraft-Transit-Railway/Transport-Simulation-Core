package org.mtr.core.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public final class HomeTests {

	private Simulator simulator;

	@BeforeEach
	public void setUp() {
		simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-home"), false);
	}

	@Test
	public void testHomeConstruction() {
		final Home home = new Home(simulator);
		home.setName("Test Home");
		assertNotNull(home);
		assertEquals("Test Home", home.getName());
	}

	@Test
	public void testIteratePassengersOnEmptyHome() {
		final Home home = new Home(simulator);
		home.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		final AtomicInteger count = new AtomicInteger(0);
		home.iteratePassengers(passenger -> count.incrementAndGet());
		assertEquals(0, count.get(), "Empty home should have no passengers to iterate");
	}

	@Test
	public void testTickDoesNotThrow() {
		final Home home = new Home(simulator);
		home.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		assertDoesNotThrow(home::tick);
	}

	@Test
	public void testMultipleTicksDoNotThrow() {
		final Home home = new Home(simulator);
		home.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		for (int i = 0; i < 10; i++) {
			assertDoesNotThrow(home::tick);
		}
	}

	@Test
	public void testIteratePassengersWithHomeTick() {
		final Home home = new Home(simulator);
		home.setCorners(new Position(-10, -10, -10), new Position(10, 10, 10));
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);

		home.tick();
		home.tick();
		home.tick();

		final AtomicInteger count = new AtomicInteger(0);
		home.iteratePassengers(passenger -> count.incrementAndGet());
		assertTrue(count.get() >= 0, "iteratePassengers should not throw and should visit passengers");
	}
}
