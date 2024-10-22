package org.mtr.core.operation;

import org.mtr.core.data.Lift;
import org.mtr.core.data.LiftDirection;
import org.mtr.core.data.LiftInstruction;
import org.mtr.core.data.Position;
import org.mtr.core.generated.operation.PressLiftSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public final class PressLift extends PressLiftSchema {

	public PressLift() {
	}

	public PressLift(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void add(Position position, LiftDirection direction) {
		instructions.add(new PressLiftInstruction(position, direction));
	}

	public void pressLift(Simulator simulator) {
		double lowestDistance = Double.MAX_VALUE;
		Lift selectedLift = null;
		LiftInstruction selectedLiftInstruction = null;

		for (final Lift lift : simulator.lifts) {
			for (final PressLiftInstruction pressLiftInstruction : instructions) {
				final LiftInstruction liftInstruction = pressLiftInstruction.getLiftInstruction(lift);
				if (liftInstruction != null) {
					double distance = lift.pressButton(liftInstruction, false);
					if (distance < lowestDistance) {
						lowestDistance = distance;
						selectedLift = lift;
						selectedLiftInstruction = liftInstruction;
						break;
					}
				}
			}
		}

		if (selectedLift != null) {
			selectedLift.pressButton(selectedLiftInstruction, true);
		}
	}
}
