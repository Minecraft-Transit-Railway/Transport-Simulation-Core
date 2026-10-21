package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class VehicleDeploymentTests {

	@Test
	public void testBlockedDeploymentIsSkipped() {
		final Vehicle vehicle = createVehicle();
		final ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> positions = createPositionSnapshots();
		final PathData pathData = vehicle.vehicleExtraData.immutablePath.getFirst();
		final VehiclePosition blocker = new VehiclePosition();
		blocker.addSegment(0, 100, Long.MAX_VALUE);
		putPosition(positions.getFirst(), pathData, blocker);

		assertFalse(vehicle.tryStartUp(0, 0, positions));
		assertFalse(vehicle.getIsOnRoute());
	}

	@Test
	public void testSuccessfulDeploymentIsImmediatelyReserved() {
		final Vehicle vehicle = createVehicle();
		final ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> positions = createPositionSnapshots();

		assertTrue(vehicle.tryStartUp(0, 0, positions));
		assertTrue(vehicle.getIsOnRoute());

		final PathData pathData = vehicle.vehicleExtraData.immutablePath.getFirst();
		final VehiclePosition reservation = Data.tryGet(positions.get(1), pathData.getOrderedPosition1(), pathData.getOrderedPosition2());
		assertNotNull(reservation);
		assertTrue(reservation.getClosestOverlap(0, 100, pathData.reversePositions, Long.MIN_VALUE) >= 0);
	}

	private static Vehicle createVehicle() {
		final Simulator simulator = new Simulator("test", new String[]{"test"}, Paths.get("build/test-data-deployment"), false);
		final Position start = new Position(0, 0, 0);
		final Position end = new Position(100, 0, 0);
		final PathData pathData = new PathData(null, 0, 0, -1, 0, 100, start, Angle.E, end, Angle.E);
		final ObjectArrayList<PathData> sidingPath = new ObjectArrayList<>();
		sidingPath.add(pathData);
		final ObjectArrayList<PathData> emptyPath = new ObjectArrayList<>();
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		cars.add(new VehicleCar("test", 10, 2, 100, 0, 5, 0.5, 0.5));
		final VehicleExtraData extraData = VehicleExtraData.create(
			0, 0, 10, cars, sidingPath, emptyPath, emptyPath, pathData,
			false, Siding.ACCELERATION_DEFAULT, Siding.ACCELERATION_DEFAULT, false, 0, 0
		);
		final Vehicle vehicle = new Vehicle(extraData, null, TransportMode.TRAIN, simulator);
		vehicle.simulate(0, null, null);
		return vehicle;
	}

	private static ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> createPositionSnapshots() {
		final ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> positions = new ObjectArrayList<>();
		positions.add(new Object2ObjectAVLTreeMap<>());
		positions.add(new Object2ObjectAVLTreeMap<>());
		return positions;
	}

	private static void putPosition(Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>> positions, PathData pathData, VehiclePosition vehiclePosition) {
		final Object2ObjectAVLTreeMap<Position, VehiclePosition> inner = new Object2ObjectAVLTreeMap<>();
		inner.put(pathData.getOrderedPosition2(), vehiclePosition);
		positions.put(pathData.getOrderedPosition1(), inner);
	}
}
