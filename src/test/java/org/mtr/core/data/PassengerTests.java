package org.mtr.core.data;

import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		simulator.sync();
	}

	@Test
	public void testConstruction() {
		final Passenger passenger = new Passenger(simulator);
		assertNotNull(passenger);
		assertTrue(passenger.isValid());
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
	public void testTickOnCooldownReturnsNotDirty() {
		final Passenger passenger = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);
		passenger.tick(home, simulator);
		assertFalse(passenger.tick(home, simulator), "On cooldown, should return not dirty");
	}

	@Test
	public void testMultiplePassengersDoNotInterfere() {
		final Passenger passenger1 = new Passenger(simulator);
		final Passenger passenger2 = new Passenger(simulator);
		simulator.setGameTime(0, Utilities.MILLIS_PER_DAY, false);

		assertTrue(passenger1.tick(home, simulator));
		assertTrue(passenger2.tick(home, simulator));

		assertFalse(passenger1.tick(home, simulator));
		assertFalse(passenger2.tick(home, simulator));
	}

	@Test
	public void testIsRouteJammedDefaultsFalse() {
		assertFalse(simulator.isRouteJammed(42), "Route should not be jammed by default");
		assertFalse(simulator.isRouteJammed(0), "Route ID 0 should not be jammed");
	}

	@Test
	public void testMarkAndClearRouteJammed() {
		simulator.markRouteJammed(42);
		assertTrue(simulator.isRouteJammed(42), "Route 42 should be jammed after marking");
		assertFalse(simulator.isRouteJammed(0), "Route 0 should not be affected");
		simulator.tick();
		assertFalse(simulator.isRouteJammed(42), "Jammed routes should be cleared after tick");
	}

	@Test
	public void testHomeIteratePassengers() {
		final Home testHome = new Home(simulator);
		testHome.setName("Iterate Home");
		testHome.setCorners(new Position(-5, -5, -5), new Position(5, 5, 5));
		assertDoesNotThrow(() -> testHome.iteratePassengers(passenger -> {
		}));
	}

	@Test
	public void testVehicleExtraDataPassengerSetsPerCar() {
		final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
		vehicleCars.add(new VehicleCar("car_1", 10, 2, 100, 0, 5, 0.5, 0.5));
		vehicleCars.add(new VehicleCar("car_2", 10, 2, 100, 0, 5, 0.5, 0.5));
		vehicleCars.add(new VehicleCar("car_3", 10, 2, 100, 0, 5, 0.5, 0.5));
		final ObjectArrayList<PathData> emptyPath = new ObjectArrayList<>();
		final PathData dummyPath = new PathData(new JsonReader(new JsonObject()));
		final VehicleExtraData vehicleExtraData = VehicleExtraData.create(
			0, 0, 10, vehicleCars, emptyPath, emptyPath, emptyPath, dummyPath, false, 0.1, 0.1, false, 0, 0
		);

		assertEquals(3, vehicleExtraData.passengers.size(), "Should have one set per car");

		final Passenger passenger = new Passenger(simulator);
		vehicleExtraData.passengers.get(1).add(passenger);
		assertTrue(vehicleExtraData.passengers.get(1).contains(passenger));
		assertFalse(vehicleExtraData.passengers.getFirst().contains(passenger));
		assertFalse(vehicleExtraData.passengers.get(2).contains(passenger));
	}

	@Test
	public void testVehicleExtraDataCopyHasEmptyPassengerSets() {
		final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
		vehicleCars.add(new VehicleCar("car_1", 10, 2, 100, 0, 5, 0.5, 0.5));
		final ObjectArrayList<PathData> emptyPath = new ObjectArrayList<>();
		final PathData dummyPath = new PathData(new JsonReader(new JsonObject()));
		final VehicleExtraData original = VehicleExtraData.create(
			0, 0, 10, vehicleCars, emptyPath, emptyPath, emptyPath, dummyPath, false, 0.1, 0.1, false, 0, 0
		);

		final Passenger passenger = new Passenger(simulator);
		original.passengers.getFirst().add(passenger);
		assertEquals(1, original.passengers.getFirst().size());

		final VehicleExtraData copy = original.copy(0);
		assertTrue(copy.passengers.getFirst().isEmpty(), "Copy must start with empty passenger sets");
	}

	@Test
	public void testWriteVehicleCacheWithDefaultsDoesNotAdd() {
		final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
		vehicleCars.add(new VehicleCar("car_0", 10, 2, 100, 0, 5, 0.5, 0.5));
		final ObjectArrayList<PathData> emptyPath = new ObjectArrayList<>();
		final PathData dummyPath = new PathData(new JsonReader(new JsonObject()));
		final VehicleExtraData vehicleExtraData = VehicleExtraData.create(
			0, 0, 10, vehicleCars, emptyPath, emptyPath, emptyPath, dummyPath, false, 0.1, 0.1, false, 0, 0
		);

		final Passenger passenger = new Passenger(simulator);
		passenger.writeVehicleCache(simulator);
		assertTrue(vehicleExtraData.passengers.getFirst().isEmpty());
	}

	@Test
	public void testWriteVehicleCacheWithMissingSidingDoesNotThrow() {
		final Passenger passenger = new Passenger(simulator);
		assertDoesNotThrow(() -> passenger.writeVehicleCache(simulator));
	}

	@Test
	public void testVehicleExtraDataPassengersAccessibleByCar() {
		final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
		vehicleCars.add(new VehicleCar("car_0", 10, 2, 100, 0, 5, 0.5, 0.5));
		vehicleCars.add(new VehicleCar("car_1", 10, 2, 100, 0, 5, 0.5, 0.5));
		final ObjectArrayList<PathData> emptyPath = new ObjectArrayList<>();
		final PathData dummyPath = new PathData(new JsonReader(new JsonObject()));
		final VehicleExtraData vehicleExtraData = VehicleExtraData.create(
			0, 0, 10, vehicleCars, emptyPath, emptyPath, emptyPath, dummyPath, false, 0.1, 0.1, false, 0, 0
		);

		final Passenger passenger1 = new Passenger(simulator);
		final Passenger passenger2 = new Passenger(simulator);
		vehicleExtraData.passengers.getFirst().add(passenger1);
		vehicleExtraData.passengers.getFirst().add(passenger2);

		assertEquals(2, vehicleExtraData.passengers.getFirst().size(), "Car 0 should have 2 passengers");
		assertTrue(vehicleExtraData.passengers.getFirst().contains(passenger1));
		assertTrue(vehicleExtraData.passengers.getFirst().contains(passenger2));
		assertFalse(vehicleExtraData.passengers.get(1).contains(passenger1), "Car 1 should not contain passenger 1");
	}

	@Test
	public void testClearVehicleReferencesDoesNotThrowOnFreshPassenger() {
		final Passenger passenger = new Passenger(simulator);
		assertDoesNotThrow(() -> passenger.clearVehicleReferences(simulator));
	}
}
