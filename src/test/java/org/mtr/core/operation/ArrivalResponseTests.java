package org.mtr.core.operation;

import org.mtr.core.data.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ArrivalResponseTests {

	@Test
	public void testSetCarDetailsPopulatesPassengerCount() {
		final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
		vehicleCars.add(new VehicleCar("car_0", 10, 2, 100, 0, 5, 0.5, 0.5));
		vehicleCars.add(new VehicleCar("car_1", 10, 2, 100, 0, 5, 0.5, 0.5));

		final ObjectArrayList<PathData> emptyPath = new ObjectArrayList<>();
		final PathData dummyPath = new PathData(new JsonReader(new JsonObject()));
		final VehicleExtraData vehicleExtraData = VehicleExtraData.create(
			0, 0, 10, vehicleCars, emptyPath, emptyPath, emptyPath, dummyPath, false, 0.1, 0.1, false, 0, 0
		);

		final Simulator simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-arrival"), false);
		final Passenger passenger = new Passenger(simulator);
		vehicleExtraData.passengers.get(0).add(passenger);

		final Route route = new Route(TransportMode.TRAIN, simulator);
		final Platform platform = new Platform(new Position(0, 0, 0), new Position(10, 0, 0), TransportMode.TRAIN, simulator);
		final ArrivalResponse arrivalResponse = new ArrivalResponse("dest", 1000, 2000, 0, true, 0, 0, route, platform);
		arrivalResponse.setCarDetails(vehicleCars, vehicleExtraData.passengers);

		assertEquals(2, arrivalResponse.getCarCount(), "Should have 2 cars");
		final double[] carCounts = {0, 0};
		arrivalResponse.iterateCarDetails(car -> {
			final int index = car.getVehicleId().equals("car_0") ? 0 : 1;
			carCounts[index] = car.getPassengerCount();
		});
		assertEquals(1, carCounts[0], 0.001, "Car 0 should have 1 passenger");
		assertEquals(0, carCounts[1], 0.001, "Car 1 should have 0 passengers");
	}

	@Test
	public void testSetCarDetailsWithNullPassengers() {
		final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
		vehicleCars.add(new VehicleCar("car_0", 10, 2, 100, 0, 5, 0.5, 0.5));

		final Route route = new Route(TransportMode.TRAIN, new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-arrival2"), false));
		final Platform platform = new Platform(new Position(0, 0, 0), new Position(10, 0, 0), TransportMode.TRAIN, new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-arrival3"), false));
		final ArrivalResponse arrivalResponse = new ArrivalResponse("dest", 1000, 2000, 0, true, 0, 0, route, platform);
		arrivalResponse.setCarDetails(vehicleCars, null);

		assertEquals(1, arrivalResponse.getCarCount(), "Should have 1 car");
		arrivalResponse.iterateCarDetails(car -> assertEquals(0, car.getPassengerCount(), 0.001, "Passenger count should be 0 when null"));
	}
}
