package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;
import org.mtr.core.tool.Angle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class RailSignalStateTests {

	@Test
	public void testSignalReservationSurvivesStateRotation() {
		final Position position1 = new Position(0, 0, 0);
		final Position position2 = new Position(10, 0, 0);
		final Rail rail = Rail.newRail(
			position1, Angle.E, position2, Angle.E, Rail.Shape.QUADRATIC,
			0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			new ObjectArrayList<>(), 80, 80, false, false, true, false, true, TransportMode.TRAIN
		);
		final SignalModification signalModification = new SignalModification(position1, position2, false);
		signalModification.putColorToAdd(1);
		rail.applyModification(signalModification);

		assertFalse(rail.isBlocked(42, Rail.BlockReservation.PRE_RESERVE));
		rail.tick1(new Client[0]);

		assertFalse(rail.isBlocked(42, Rail.BlockReservation.DO_NOT_RESERVE));
		assertTrue(rail.isBlocked(99, Rail.BlockReservation.DO_NOT_RESERVE));
	}
}
